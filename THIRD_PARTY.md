# 第三方来源

## libxposed API 100

- 官方仓库：https://github.com/libxposed/api
- 固定提交：`e2588ee24bbef12c7c2e07666efa60e58e1ee6b3`
- 文件：`libxposed-api100/src/main/java/`，原始 Java 源码逐字保留。
- 许可证：Apache-2.0，全文见 `libxposed-api100/LICENSE`。
- `libxposed-api100/UPSTREAM.json` 记录每个上游文件的 Git blob SHA-1。
- 为方便独立编译，本工程单独提供 Android library 构建文件；这不是伪造的 API stub。
- API 只参与编译，不进入 APK，由 LSPosed 在目标进程提供实现。

注意：官方早期 `100` 标签与后来 LSPosed 使用的 API 100 接口并不完全相同。
本工程使用带 `XposedInterface` 构造参数、`@XposedHooker` 和 `@BeforeInvocation`
的后期 API 100，且没有使用 API 101/102 的初始化方式。

## Gradle Wrapper 8.9

- 官方来源：https://github.com/gradle/gradle/tree/v8.9.0
- 文件：`gradlew`、`gradlew.bat`、`gradle/wrapper/gradle-wrapper.jar`。
- Apache-2.0 许可证全文同附带的 `libxposed-api100/LICENSE`。
- Wrapper SHA-256：`498495120a03b9a6ab5d155f5de3c8f0d986a449153702fb80fc80e134484f17`。
- 官方校验值：https://services.gradle.org/distributions/gradle-8.9-wrapper.jar.sha256

百度地图的原始 smali、APK、资源及账号数据不包含在本工程中。
