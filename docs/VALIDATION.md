# 交付验证记录

日期：2026-09-06。

| 验证项 | 结果 |
| --- | --- |
| 13 份输入 smali 归档解压及全量文字检索 | 完成 |
| 自动重置页面栈分支的静态调用链 | 已核实；`verify_smali.py` 通过 |
| 官方 API 100 源码完整性 | 每份源文件与固定提交的 Git blob SHA-1 一致 |
| Gradle Wrapper | 与 Gradle 官方公布 SHA-256 一致 |
| XML、模块入口、作用域和 min/target API | 检查通过 |
| Java 编译 | OpenJDK 17.0.20 编译通过，无错误或警告 |
| 编译依赖 | Google Android SDK Platform 35 r02 的真实 android.jar、官方 API 100 源码、AndroidX annotation 1.8.2、Kotlin stdlib 1.9.24（供注解元数据解析） |
| JVM API 契约检查 | 构造参数、运行时 Hook 注解、public static 回调、void 跳过调用、空窗口对象放行检查通过 |
| GitHub Actions YAML | 解析通过；实际远端 workflow 未运行 |
| 完整 Gradle APK 构建 | 未完成：本地 Wrapper 下载 Gradle 时网络连接失败；使用环境提供的代理配置重试仍失败 |
| APK 元数据及 DEX 检查 | 已提供验证工具并接入 CI；本地未生成 APK，未执行此项 |
| 真机/车机运行 | 未测试 |
| 弹窗唯一入口与资源布局对应 | 未能确认；附件缺少资源文件、完整 APK 及运行时栈 |

`tools/ApiContractCheck.java` 使用真实 libxposed API 类型，
只将 BeforeHookCallback 替换为一个记录调用次数的检查对象。
它不模拟 Android 窗口生命周期，也不证明车机业务功能已经通过实测。

交付源码已通过如下检查：

```text
PASS: API 100 constructor, runtime annotations, callback ABI and skip contract
PASS: official API 100 sources, wrapper checksum, XML, entry, scope and dependencies
PASS: auto UI task contains only known UI calls; state updates precede the skipped task
```

不能据此将“源码可编译”“hook 的静态边界正确”升级为
“所有弹窗实现均覆盖”或“所有未来百度地图版本永久兼容”。

## 可复核的官方来源

- API 100 固定源码：https://github.com/libxposed/api/tree/e2588ee24bbef12c7c2e07666efa60e58e1ee6b3
- LSPosed 1.9.2 的 modern API 加载构造约定：https://github.com/LSPosed/LSPosed/blob/v1.9.2/core/src/main/java/org/lsposed/lspd/impl/LSPosedContext.java
- Gradle 8.9 Wrapper 校验：https://services.gradle.org/distributions/gradle-8.9-wrapper.jar.sha256
- Android Platform 35 r02：https://dl.google.com/android/repository/platform-35_r02.zip

源码工程只固定 Xposed API 100，并未把 Android SDK 版本写成 100。
