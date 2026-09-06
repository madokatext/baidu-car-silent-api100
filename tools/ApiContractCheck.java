import java.lang.reflect.Member;
import java.lang.reflect.Modifier;

import io.github.baiducarsilent.SilentModule;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface;
import io.github.libxposed.api.annotations.BeforeInvocation;
import io.github.libxposed.api.annotations.XposedHooker;

/** JVM contract check against the real API. Does not simulate a car or Android window lifecycle. */
public final class ApiContractCheck {
    private static final class Callback implements XposedInterface.BeforeHookCallback {
        int skips;
        Object result = new Object();
        public Member getMember() { return null; }
        public Object getThisObject() { return null; }
        public Object[] getArgs() { return new Object[0]; }
        public void returnAndSkip(Object value) { skips++; result = value; }
        public void throwAndSkip(Throwable value) { throw new AssertionError(value); }
    }

    private static void require(boolean value, String name) {
        if (!value) throw new AssertionError(name);
    }

    public static void main(String[] args) throws Exception {
        require(XposedInterface.API == 100, "API 100");
        SilentModule.class.getConstructor(XposedInterface.class,
                XposedModuleInterface.ModuleLoadedParam.class);
        for (Class<?> type : new Class<?>[]{SilentModule.AutoUiHook.class,
                SilentModule.DialogHook.class, SilentModule.PopupHook.class}) {
            require(type.isAnnotationPresent(XposedHooker.class), "hooker annotation");
            var before = type.getDeclaredMethod("before", XposedInterface.BeforeHookCallback.class);
            require(Modifier.isPublic(before.getModifiers()) && Modifier.isStatic(before.getModifiers()),
                    "public static API-100 callback");
            require(before.isAnnotationPresent(BeforeInvocation.class), "before annotation");
        }
        Callback auto = new Callback();
        SilentModule.AutoUiHook.before(auto);
        require(auto.skips == 1 && auto.result == null, "void UI task returns and skips once");
        Callback absentWindow = new Callback();
        SilentModule.DialogHook.before(absentWindow);
        SilentModule.PopupHook.before(absentWindow);
        require(absentWindow.skips == 0, "non-window receiver left untouched");
        System.out.println("PASS: API 100 constructor, runtime annotations, callback ABI and skip contract");
    }
}
