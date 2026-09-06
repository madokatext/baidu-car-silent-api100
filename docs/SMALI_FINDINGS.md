# smali 定位证据

分析日期：2026-09-06。附件标记构建渠道 `BaiduMap_oppoX64Release`；
由于没有 AndroidManifest.xml 或 APK，不推测具体百度地图版本号。
输入归档及关键文件 SHA-256 见 `input-provenance.json`。

## 已确认的自动界面分支

`classes7_smali(3).zip` 中：

| 文件 / 方法 | smali 文件行号 | 核实结果 |
| --- | --- | --- |
| `CarTransferCallback.onReceiveMessage(...)` | 2561–2639 | “车机开始导航”分支先设置状态、执行长连接相关操作和 `k()` 事件，然后按手机导航状态决定是否调用 `e()` |
| `CarTransferCallback.e()V` | 324 | 创建 `checkTopPageAndShow$1`，交给 `runOnMainForData$default` 调度 |
| `CarTransferCallback$checkTopPageAndShow$1.invoke()V` | 136 | 当前不是控制页时，调用 `c(callback)`，随后 `u1.d.h("auto")` |
| `CarTransferCallback.c(callback)` | 195 | 仅代理到 `j()` |
| `CarTransferCallback.j()V` | 1329 | 调用 `TaskManager.resetToRootRecord()` |

`classes4_smali(4).zip` 中 `u1/d.smali` 第 164 行起的 `h(String)`
将参数存入 Bundle 的 `from`，然后 `navigateTo` 到
`com.baidu.baidumaps.carconnect.CarConnectControlPage`。

因此，“阻止自动打断当前操作”的精确 UI 边界是上面的调度任务。
将最终页面做透明、在页面打开后关闭、只拦截 `navigateTo()`，都会太晚，
因为页面栈可能已经重置。将 `onReceiveMessage()` 整体替换为空，则会漏掉真正的业务状态更新。

模块不直接 hook 可能重混淆的 `j()` 或 `u1.d.h()`，而是 hook 可识别的具名 Lambda，
以及由其 EnclosingMethod 反射得到的调度函数。对其 Kotlin bridge 和
`onReceiveMessage` 调用者进行定点去优化，降低 ART 内联绕过 hook 的风险。

## 关于“手车已互联”文字

全量解压与检索 13 个输入归档后，字面量出现在 `classes6_smali(4).zip`：

| 类 | 实际用途 |
| --- | --- |
| `LuiSearchPresenter` | 常量、搜索框 hint 状态更新，调用 `showSearchBoxTx()` |
| `LuiInputPresenter` | 设置/比较输入框 hint；接管键盘前可重置该 hint |
| `LuiInputTextSwitcherPresenter` | 提示文字轮播相关逻辑 |

这些命中本身不能证明就是用户看到的弹窗；本模块保留它们。
标准 Dialog/PopupWindow 的运行时文字筛选是兜底功能，不是已经确认的唯一弹窗入口。

## 保留的边界

- 绑定/解绑请求、CarTransferConfig 的真实状态读写。
- 长连接接收、注册、消息回复、状态事件。
- 发送位置/路线/指令以及手动控制页入口。
- 其它正常弹窗、搜索框文字与连接状态栏。

以上描述的是 hook 选择和静态代码边界，不是所有互联功能的端到端测试结论。
原始 smali 没有随工程重新分发。
