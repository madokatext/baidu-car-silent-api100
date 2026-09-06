# 百度手车互联静默 · libxposed API 100

基于所提供的 13 份百度地图 smali 制作的 LSPosed 模块工程。
包含完整源码、固定版本的官方 API 100 源码、Gradle Wrapper、GitHub Actions。
项目仓库：https://github.com/madokatext/baidu-car-silent-api100

**验证状态：GitHub Actions 已成功构建 Debug / Release APK；
Debug APK 的模块元数据及 DEX 检查通过；未进行手机/车机实测。
现有附件不能唯一确定所述弹窗的生成入口，因此不宣称已验证“永远不弹窗且所有互联功能零影响”。**

[首次成功构建](https://github.com/madokatext/baidu-car-silent-api100/actions/runs/34037935979) ·
[下载该次 APK 产物](https://github.com/madokatext/baidu-car-silent-api100/actions/runs/34037935979/artifacts/9990781628)

## 改哪里

已确认的自动打断路径位于：

```text
CarTransferCallback.onReceiveMessage(...)
  接收车机开始导航消息，更新连接/导航状态、发送状态事件
  调用 e()，调度 checkTopPageAndShow$1
  checkTopPageAndShow$1.invoke()V
    检查当前页面
    c(callback) → j() → TaskManager.resetToRootRecord()
    u1.d.h("auto") → navigateTo(CarConnectControlPage)
```

这个版本里只挡 `navigateTo()` 或弹窗 `show()` 都不足以阻止前面的页面栈重置。
模块在 `CarTransferCallback$checkTopPageAndShow$1.invoke()V` 执行前返回；
同时使用该类的 `EnclosingMethod` 元数据定位并拦截其调度方法（本版本为 `e()`）。
不通过猜测混淆名寻找未知版本的方法。

该 UI 分支之前的状态更新、长连接消息处理仍执行。
没有拦截 `onReceiveMessage()` 整体，没有修改 `CarTransferConfig`，
没有全局拦截页面栈操作，也没有拦截手动进入控制页的 `u1.d.h("lui")` 等入口。

## 弹窗处理及范围

附件中的“手车已互联”字面量出现于搜索框 hint 相关代码；未包含 `res/`、
`resources.arsc`、完整 APK 或运行时栈，不能把它直接等同于某个 Dialog 标题。
模块另外提供以下运行时兜底：

| 对象 | 处理 |
| --- | --- |
| 主进程中标准 `Dialog.show()` | 调用 `create()` 完成尚未执行的布局初始化；可读取 TextView 完整文字为“手车已互联”时，阻止窗口显示 |
| `PopupWindow.showAtLocation()` / `showAsDropDown()` | 内容视图中存在同样完整文字时阻止显示 |
| 其他标题的窗口 | 正常显示 |
| 搜索框 hint、手车状态栏 | 保留；不是该模块要隐藏的弹窗 |
| WebView、图片文字、原生绘制、异步填充文字、自定义浮层 | 没有宣称被上述文本筛选覆盖 |

文本匹配是去掉首尾空白后的完全相等，不按“车”“互联”等泛词屏蔽。
不会模拟点击“取消/断开”，不会弹出后再 dismiss；日志不记录 VIN、账号、位置或消息正文。
没有循环检测、网络权限、后台服务或设置界面。

必须注意：`Dialog.create()` 仍会执行该 Dialog 的 `onCreate()`；
调用 `show()` 前已经发生的应用行为，以及未知 Dialog 对 `onShow/onDismiss`
回调的业务依赖，都不能靠通用文本筛选证明无影响。
已确认的自动重置页面栈路径由上游专用 hook 处理；其它未知路径不作保证。

## GitHub Actions 构建

1. 打开本仓库的 [Actions 页面](https://github.com/madokatext/baidu-car-silent-api100/actions)。
2. 选择 **Build API 100 module**，点击 **Run workflow**；push 和 pull request 也会触发。
3. 成功后下载 `BaiduCarSilent-API100` artifact。
4. `app-debug.apk` 已由 Android debug key 签名，可直接安装；
   `app-release-unsigned.apk` 是未签名构建，需要自行签名后安装。

不同 GitHub runner 的临时 debug key 可能不同，跨次构建升级安装时可能出现签名不一致。
需要连续覆盖升级时，应使用自己固定的签名密钥签署 release APK。

## 启用

- 安装构建得到的 APK。
- 在支持本工程所用 modern API 100 接口的 LSPosed 中启用模块。
- 作用域只选择百度地图：`com.baidu.BaiduMap`。
- 强行停止后重新打开百度地图，让新进程加载模块；模块没有桌面入口。
- LSPosed 模块日志中的 `[BaiduCarSilent] hooked ...` 表示 hook 安装成功；
  `blocked automatic car-control page` 表示已拦截自动切页，
  `blocked dialog/popup ...` 表示已拦截对应标准窗口。
- 停用模块并重启百度地图进程即可恢复原行为；模块不会改写百度地图配置或连接状态。

## 本地构建

环境：JDK 17、Android SDK Platform 35、可联网获取 Gradle/Android Gradle Plugin 的环境。
固定 Gradle 8.9、Android Gradle Plugin 8.7.3，Android 最低版本为 8.0（API 26）。
Android SDK 版本与 Xposed API 100 是两套独立编号。

```bash
chmod +x gradlew
./gradlew :app:assembleDebug
```

Windows 使用 `gradlew.bat :app:assembleDebug`。SDK 路径可在 `local.properties`
中指定 `sdk.dir`，或使用 `ANDROID_HOME`。

## API 与验证

`libxposed-api100/` 是官方 `libxposed/api` 提交
`e2588ee24bbef12c7c2e07666efa60e58e1ee6b3` 的真实源码，API 常量为 100。
使用 `compileOnly(project(":libxposed-api100"))`，不会把 API 实现打入 APK。
不依赖私人 Maven 仓库、GitHub Packages token 或不固定版本的 JitPack 构建。

```bash
python3 tools/verify_project.py
python3 tools/verify_smali.py /path/to/extracted/smali
python3 tools/verify_project.py --apk app/build/outputs/apk/debug/app-debug.apk
```

检查工具验证上游源码 Git blob 校验值、官方 Wrapper 校验值、XML 和模块元数据。
APK 检查会解析 DEX 的类定义表，确认 API 没被重复打包；不会把 API 的正常类型引用
误判为重复定义。

详见 [smali 定位证据](docs/SMALI_FINDINGS.md)、[交付验证记录](docs/VALIDATION.md)、
[第三方来源](THIRD_PARTY.md)。
