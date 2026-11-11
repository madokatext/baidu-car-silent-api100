package io.github.baiducarsilent;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.annotations.BeforeInvocation;
import io.github.libxposed.api.annotations.XposedHooker;
import io.github.libxposed.api.errors.XposedFrameworkError;

/** UI-only hooks. No connection-state writes, network hooks or synthetic button clicks. */
public final class SilentModule extends XposedModule {
    private static final String PACKAGE = "com.baidu.BaiduMap";
    private static final String CALLBACK =
            "com.baidu.baidumaps.process.callback.CarTransferCallback";
    private static final String AUTO_TASK = CALLBACK + "$checkTopPageAndShow$1";
    private static final String UPGRADE_PACKAGE = "com.baidu.baidumaps.common.upgrade.";
    private static final String UPGRADE_CONTROLLER = UPGRADE_PACKAGE + "VersionUpdateController";
    private static final String DOWNLOAD_DIALOG = UPGRADE_PACKAGE + "VersionDownloadDialogWrapper";
    private static final String TITLE = "手车已互联";
    private static final AtomicInteger BLOCK_LOGS = new AtomicInteger();
    private static final AtomicInteger ERROR_LOGS = new AtomicInteger();
    private static volatile SilentModule instance;

    private final boolean mainProcess;
    private final Set<Method> installed = new HashSet<>();
    private boolean initialized;

    public SilentModule(XposedInterface base, ModuleLoadedParam param) {
        super(base, param);
        mainProcess = !param.isSystemServer() && PACKAGE.equals(param.getProcessName());
        if (mainProcess) instance = this;
    }

    @Override
    public synchronized void onPackageLoaded(PackageLoadedParam param) {
        if (!mainProcess || !PACKAGE.equals(param.getPackageName()) || initialized) return;
        initialized = true;
        log("[BaiduCarSilent] API 100; installing UI hooks");
        installAutoUiHooks(param.getClassLoader());
        installVersionUpgradeHooks(param.getClassLoader());
        installWindowHooks();
    }

    private void installVersionUpgradeHooks(ClassLoader loader) {
        // Audited in the supplied smali: dispatch, recommendation, force-upgrade,
        // and the actual WebView-backed update/install dialog, respectively.
        for (String name : new String[]{"q0", "i0", "g0", "t0"}) {
            installUpgradeVoidMethod(loader, UPGRADE_CONTROLLER, name);
        }
        // e() delegates to f(); f() also restores a hidden download dialog.
        installUpgradeVoidMethod(loader, DOWNLOAD_DIALOG, "f");

        try {
            Class<?> startup = Class.forName(
                    "com.baidu.baidumaps.common.app.startup.e", false, loader);
            Class<?> handler = Class.forName(
                    "com.baidu.mapframework.nirvana.looper.MainLooperHandler", false, loader);
            Method method = startup.getDeclaredMethod("h", int.class, Context.class,
                    DialogInterface.OnClickListener.class, DialogInterface.OnClickListener.class,
                    handler);
            requireInstanceVoid(method);
            // This startup helper is shared. Only kind=1 belongs to version upgrade;
            // kind=0 has a separate startup flow and must remain untouched.
            install(method, UpgradeStartupHook.class);
            deopt(method);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException | XposedFrameworkError e) {
            reportError("version-upgrade startup hook unavailable", e);
        }

        // Deoptimize the known call sites, including asynchronous message/download
        // callbacks. No global class-loader, Handler or network interception.
        for (String name : new String[]{UPGRADE_CONTROLLER, DOWNLOAD_DIALOG,
                UPGRADE_CONTROLLER + "$a", UPGRADE_CONTROLLER + "$d",
                UPGRADE_CONTROLLER + "$e", UPGRADE_CONTROLLER + "$l",
                UPGRADE_CONTROLLER + "$DownloadStateReceiver"}) {
            try {
                Class<?> caller = Class.forName(name, false, loader);
                for (Method method : caller.getDeclaredMethods()) {
                    if (!Modifier.isAbstract(method.getModifiers())
                            && !Modifier.isNative(method.getModifiers())) deopt(method);
                }
            } catch (ReflectiveOperationException | LinkageError | RuntimeException | XposedFrameworkError e) {
                reportError("version-upgrade caller deoptimization unavailable: " + name, e);
            }
        }
    }

    private void installUpgradeVoidMethod(ClassLoader loader, String className, String name) {
        try {
            Method method = Class.forName(className, false, loader).getDeclaredMethod(name);
            requireInstanceVoid(method);
            install(method, VersionUpgradeHook.class);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException | XposedFrameworkError e) {
            // Keep the other independent upgrade hooks and the window fallback active.
            reportError("version-upgrade hook unavailable: " + className + "." + name, e);
        }
    }

    private static void requireInstanceVoid(Method method) throws NoSuchMethodException {
        if (method.getReturnType() != void.class || Modifier.isStatic(method.getModifiers())) {
            throw new NoSuchMethodException("expected instance void method: " + method);
        }
    }

    private static boolean isVersionUpgradeWindow() {
        // A dedicated upgrade-package caller is stronger evidence than visible words
        // such as "update", which also occur in offline-map/voice/data dialogs.
        for (StackTraceElement frame : new Throwable().getStackTrace()) {
            if (frame.getClassName().startsWith(UPGRADE_PACKAGE)) return true;
        }
        return false;
    }

    private void installAutoUiHooks(ClassLoader loader) {
        try {
            Class<?> task = Class.forName(AUTO_TASK, false, loader);
            Class<?> owner = Class.forName(CALLBACK, false, loader);
            Method invokeVoid = null;
            for (Method m : task.getDeclaredMethods()) {
                if (m.getName().equals("invoke") && m.getParameterCount() == 0
                        && m.getReturnType() == void.class && !Modifier.isStatic(m.getModifiers())) {
                    invokeVoid = m;
                    break;
                }
            }
            if (invokeVoid == null) throw new NoSuchMethodException(AUTO_TASK + ".invoke()V");
            install(invokeVoid, AutoUiHook.class);

            // Dalvik EnclosingMethod identifies e() in the supplied smali. Avoid guessing
            // obfuscated names in future versions. The scheduler only queues the UI task.
            Method enclosing = task.getEnclosingMethod();
            if (enclosing != null && enclosing.getDeclaringClass() == owner
                    && enclosing.getParameterCount() == 0 && enclosing.getReturnType() == void.class
                    && !Modifier.isStatic(enclosing.getModifiers())) {
                install(enclosing, AutoUiHook.class);
                deopt(enclosing);
            } else {
                log("[BaiduCarSilent] scheduler metadata unavailable; task hook remains active");
            }
            // Deoptimize the actual callers, including the Kotlin bridge, not the whole app.
            for (Method m : task.getDeclaredMethods()) {
                if (m.isBridge() && m.getName().equals("invoke")) deopt(m);
            }
            for (Method m : owner.getDeclaredMethods()) {
                if (m.getName().equals("onReceiveMessage")) deopt(m);
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException | XposedFrameworkError e) {
            reportError("automatic-page hook unavailable; standard-window filter still attempted", e);
        }
    }

    private void installWindowHooks() {
        try {
            install(Dialog.class.getDeclaredMethod("show"), DialogHook.class);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException | XposedFrameworkError e) {
            reportError("Dialog hook unavailable", e);
        }
        for (Method m : PopupWindow.class.getDeclaredMethods()) {
            if (m.getReturnType() == void.class && !Modifier.isStatic(m.getModifiers())
                    && (m.getName().equals("showAtLocation") || m.getName().equals("showAsDropDown"))) {
                try {
                    install(m, PopupHook.class);
                } catch (LinkageError | RuntimeException | XposedFrameworkError e) {
                    reportError("PopupWindow hook unavailable: " + m.getName(), e);
                }
            }
        }
    }

    private void install(Method method, Class<? extends Hooker> hooker) {
        if (installed.contains(method)) return;
        method.setAccessible(true);
        hook(method, PRIORITY_HIGHEST, hooker);
        installed.add(method);
        log("[BaiduCarSilent] hooked " + method.getDeclaringClass().getName() + "." + method.getName());
    }

    private void deopt(Method method) {
        try {
            if (!deoptimize(method)) log("[BaiduCarSilent] deopt returned false: " + method.getName());
        } catch (LinkageError | RuntimeException | XposedFrameworkError e) {
            reportError("caller deoptimization unavailable", e);
        }
    }

    static boolean containsTargetTitle(View root) {
        if (root == null) return false;
        ArrayDeque<View> pending = new ArrayDeque<>();
        pending.push(root);
        // One traversal when a window opens. No observers, polling or global TextView hooks.
        int visited = 0;
        while (!pending.isEmpty() && visited++ < 1024) {
            View view = pending.pop();
            if (view instanceof TextView textView) {
                CharSequence text = textView.getText();
                if (text != null && TITLE.equals(text.toString().trim())) return true;
            }
            if (view instanceof ViewGroup group) {
                // Bound queued views as well as the number visited.
                for (int i = group.getChildCount() - 1; i >= 0 && pending.size() < 1024; i--) {
                    View child = group.getChildAt(i);
                    if (child != null) pending.push(child);
                }
            }
        }
        return false;
    }

    private static void blocked(String kind) {
        SilentModule module = instance;
        if (module != null && BLOCK_LOGS.getAndIncrement() < 20) {
            module.log("[BaiduCarSilent] blocked " + kind);
        }
    }

    private static void reportError(String message, Throwable error) {
        SilentModule module = instance;
        if (module != null && ERROR_LOGS.getAndIncrement() < 10) {
            module.log("[BaiduCarSilent] " + message, error);
        }
    }

    @XposedHooker
    public static final class AutoUiHook implements Hooker {
        @BeforeInvocation
        public static void before(BeforeHookCallback callback) {
            // Stops BOTH resetToRootRecord() and navigateTo(...CarConnectControlPage).
            // The enclosing onReceiveMessage() continues and keeps protocol/state work.
            callback.returnAndSkip(null);
            blocked("automatic car-control page");
        }
    }

    @XposedHooker
    public static final class VersionUpgradeHook implements Hooker {
        @BeforeInvocation
        public static void before(BeforeHookCallback callback) {
            callback.returnAndSkip(null);
            blocked("version-upgrade UI");
        }
    }

    @XposedHooker
    public static final class UpgradeStartupHook implements Hooker {
        @BeforeInvocation
        public static void before(BeforeHookCallback callback) {
            Object[] args = callback.getArgs();
            if (args.length > 0 && Integer.valueOf(1).equals(args[0])) {
                callback.returnAndSkip(null);
                blocked("version-upgrade startup prompt");
            }
        }
    }

    @XposedHooker
    public static final class DialogHook implements Hooker {
        private static final ThreadLocal<Boolean> INSPECTING = new ThreadLocal<>();

        @BeforeInvocation
        public static void before(BeforeHookCallback callback) {
            if (!(callback.getThisObject() instanceof Dialog dialog) || dialog.isShowing()
                    || Boolean.TRUE.equals(INSPECTING.get())) return;
            if (isVersionUpgradeWindow()) {
                callback.returnAndSkip(null);
                blocked("dialog from version-upgrade package");
                return;
            }
            INSPECTING.set(true);
            try {
                // create() performs onCreate/inflation without attaching a window.
                // AlertDialog's message TextViews commonly do not exist before this call.
                dialog.create();
                Window window = dialog.getWindow();
                if (window != null && containsTargetTitle(window.getDecorView())) {
                    callback.returnAndSkip(null);
                    blocked("dialog with exact connection title");
                }
            } catch (RuntimeException | LinkageError e) {
                // Unknown window implementation: leave its normal show() untouched.
                reportError("cannot inspect dialog; allowing original show", e);
            } finally {
                INSPECTING.remove();
            }
        }
    }

    @XposedHooker
    public static final class PopupHook implements Hooker {
        @BeforeInvocation
        public static void before(BeforeHookCallback callback) {
            if (!(callback.getThisObject() instanceof PopupWindow popup)) return;
            if (isVersionUpgradeWindow()) {
                callback.returnAndSkip(null);
                blocked("popup from version-upgrade package");
                return;
            }
            try {
                if (containsTargetTitle(popup.getContentView())) {
                    callback.returnAndSkip(null);
                    blocked("popup with exact connection title");
                }
            } catch (RuntimeException | LinkageError e) {
                reportError("cannot inspect popup; allowing original show", e);
            }
        }
    }
}
