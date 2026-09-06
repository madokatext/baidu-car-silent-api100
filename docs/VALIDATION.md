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
| GitHub Actions | 首次远端运行成功，所有步骤通过，run ID `34037935979` |
| 完整 Gradle APK 构建 | GitHub runner 上 `:app:assembleDebug :app:assembleRelease` 成功，构建日志为 `BUILD SUCCESSFUL in 1m 13s` |
| APK 元数据及 DEX 检查 | CI 对实际生成的 Debug APK 检查通过：入口、API 100 元数据和作用域正确，未打包 libxposed API 或百度类定义 |
| APK 产物上传 | 成功上传 2 个 APK；artifact 名称为 `BaiduCarSilent-API100`，ID `9990781628` |
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

## 远端构建记录

- 已构建源码提交：`460de6162b6c1f867e7ae611767415971ae8a27a`。
- [成功运行与完整日志](https://github.com/madokatext/baidu-car-silent-api100/actions/runs/34037935979)。
- [APK 下载](https://github.com/madokatext/baidu-car-silent-api100/actions/runs/34037935979/artifacts/9990781628)：Debug 已签名；Release 未签名。
- GitHub 报告的产物 ZIP SHA-256：`b2bc7fb22bd4d7ceb13d5624948f02d10b973c878b74e089ba4323ef2a08dc92`。
- 该产物当前到期日为 2026-12-05；到期后可以手动运行 workflow 重新生成。

首次本地交付时，Wrapper 下载 Gradle 受环境网络限制而未能生成 APK。
上表的完整构建和 APK 检查结果来自此次真实 GitHub Actions 运行。
这次记录更新只修改文档，模块源码与构建配置保持在上述已通过的提交版本。

## 可复核的官方来源

- API 100 固定源码：https://github.com/libxposed/api/tree/e2588ee24bbef12c7c2e07666efa60e58e1ee6b3
- LSPosed 1.9.2 的 modern API 加载构造约定：https://github.com/LSPosed/LSPosed/blob/v1.9.2/core/src/main/java/org/lsposed/lspd/impl/LSPosedContext.java
- Gradle 8.9 Wrapper 校验：https://services.gradle.org/distributions/gradle-8.9-wrapper.jar.sha256
- Android Platform 35 r02：https://dl.google.com/android/repository/platform-35_r02.zip

源码工程只固定 Xposed API 100，并未把 Android SDK 版本写成 100。
