# 应用版本升级弹窗定位（1.1.0）

日期：2026-11-11。来源为用户上传的 `classes6_smali(4).zip`，
使用其中的 `com/baidu/baidumaps/common/upgrade/` 和 `common/app/startup/` 文件。
本次只阅读调用链并修改源码；按用户要求未运行测试、编译、验证脚本或 Actions。

## 显示链路

1. `VersionUpdateController.q0()V`（原 smali 第 3699 行起）根据 `u` 中的结果码分发 UI。
   推荐分支调用 `i0()`，强制升级分支调用 `g0()`。
2. `i0()V`（第 2884 行起）记录 `onVersionRecommend`，调用启动辅助类
   `com.baidu.baidumaps.common.app.startup.e.h(...)`，第一个参数固定为 `1`。
3. 启动辅助类的 `h(...)` 可能显示流量提示；随后 `f(...)` 对升级分支调用 `j(...)`，
   向传入 Handler 发送 `what=100` 的消息。
4. `VersionUpdateController$l.onMessage(Message)` 对消息 `100` 调用控制器的 `t0()`。
5. `t0()V`（第 4053 行起）调用 `U()` 创建带 SafeWebView 的内容视图，
   调用 `N(View)` 构造 BMAlertDialog，然后显示窗口、记录弹窗日期并调度统计任务。
   `U()` 根据文件状态设置“安装更新”“一键更新（不耗流量）”或“下载更新”。
6. `g0()V`（第 2624 行起）独立构造 SafeWebView 和不可取消的 BMAlertDialog，
   按钮为“版本升级”和“退出程序”。不能只拦普通推荐升级入口。

## 已下载文件和下载窗口

- `VersionUpdateController$DownloadStateReceiver.onReceive(...)` 在静默下载完成的分支
  通过合成方法 `VersionUpdateController.c(controller)` 再调用 `i0()`。
- `VersionUpdateController.S(boolean)` 创建 `VersionDownloadDialogWrapper`，调用 `e()`；
  后者直接调用 `f()` 创建并显示下载窗口。控制器 `L(boolean)` 也会直接调用 `f()`
  恢复已隐藏的下载窗口。
- `VersionDownloadDialogWrapper.h()` 只在窗口引用非空且处于显示状态时更新界面；
  跳过 `f()` 后不创建窗口，不需要伪造下载状态。

因此同时拦截 `q0 / i0 / g0 / t0` 与下载窗口 `f()`，避免只挡住首次弹窗，
却遗漏异步 Handler、已下载 APK 的提醒或下载窗口恢复路径。
每个方法都独立安装 hook，某一方法不存在时仍尝试其余入口。

## 拦截边界

- 所有直接拦截的方法必须是已定位的实例 `void` 方法；显示前 `returnAndSkip(null)`。
- 共享启动辅助方法只在参数类型和签名匹配，且第一个参数为 `1` 时跳过。
  不拦截参数为 `0` 的其他启动分支，也不伪造 Handler 完成消息。
- 对控制器、下载窗口及其已知异步调用方的方法执行定向反优化，降低旧编译代码内联的影响。
- 标准窗口过滤还检查当前调用栈中是否存在 `com.baidu.baidumaps.common.upgrade.` 包，
  作为同包其他窗口展示入口的兜底；无需解析 WebView 或等待异步文字填充。
- 不通过“版本”“更新”这类文本屏蔽整个应用的提示，不修改应用版本号、连接状态、
  更新服务端结果或手车互联协议，也不拦截整个 Handler、网络请求或类加载器。
- 手动检查更新仍可发起请求，但其上述应用升级窗口也会被抑制。
- 不覆盖应用商店或系统安装器等外部进程，也不宣称兼容所有未来百度地图版本。

本次没有 APK 构建或真机结果；1.0.0 的历史构建不能用于证明该新增功能有效。
