# 0.6 实现与边界

## 文件职责

| 文件 | 职责 |
| --- | --- |
| `VivoBridge.java` | 厂商、进程、当前锁屏壁纸、AOD 开关与主屏状态校验；安装兼容钩子 |
| `NativeAodLifecycle.java` | 高优先级显示回调范围，原生 AOD 请求状态诊断 |
| `AodWindowController.java` | 原生 AOD / 黑色覆盖窗口 / 精确遮罩背景的修改与生命周期恢复 |
| `NativeClockController.java` | SystemUI 内模块时钟就绪检测与原生时钟绘制门控 |
| `BridgePolicy.java` | 不依赖 Android 的匹配与状态策略 |
| `MainActivity.java` | 单个桌面入口和只读安装状态页 |

Xposed 入口是 `com.qihang.vivobridge.VivoBridge`。仅对 vivo / iQOO，以及 `android`、`com.android.systemui`、`com.vivo.nightpearl` 三个目标包安装。构建桩不会进入运行时 DEX。

## 电源流程

对 DisplayPowerController / DisplayPowerControllerImpl / DisplayPowerState 的相关显示方法建立 ThreadLocal 回调深度。只有 HyperAlive 的显示 before-hook 直接调用 StateCoordinator.isAodEnabled 时，才让该次读取视为关闭，避免原模块将 OFF 改为 DOZE 或跳过 prepareColorFade。其他 StateCoordinator 读取继续看到系统的 screen_off_remind 映射值。

这个设计让 vivo 保持自己的原生 AOD 状态机，不直接调用面板驱动、不写亮度、不改系统 APK。匹配依赖原模块类名和调用结构，因此固定以 HyperAlive 1.0.3 为测试基线。

## 显示与恢复

暴露壁纸的前提包括厂商、原生 AOD 开关、HyperAlive 锁屏组件、主屏 displayId=0、PowerManager 非交互状态和睡眠时间戳未早于唤醒时间戳。其他壁纸和副屏不应用。

窗口控制器保存自己将修改的背景、格式、alpha 和壁纸标志，监听附着/移除、屏幕事件和相关设置。最多约 1.1 秒的有限重试覆盖电源事件与属性写入的顺序，不建立永久轮询或持有 WakeLock。恢复只还原本模块负责的字段，壁纸标志按原来是否存在恢复，不重写整套 flags。

SystemUI 背景目标为已在本机视图树确认的 `aod_root_view_bg_bottom/top`、`aod_off_mask`、`full_aod_mask_view`、`full_aod_mask`，且资源所属包必须匹配；不按模糊名称处理其他应用。

## 时钟所有权

0.6 只在 SystemUI 安装 NativeClockController。它发现 FlymeClockView 后收集同根视图内已识别的 vivo 时间/日期分支，在模块 AOD 时钟的模式、尺寸、窗口可见性、父链 alpha 和时间文字都满足要求时，跳过对应 `ViewGroup.drawChild`。失去就绪条件会保留原生绘制。

这不是完整的 OriginOS 锁屏替换器。NightPearl 进程在 0.6 中只收到窗口透明化兼容，没有模块时钟宿主接入和跨进程时钟所有权交接。因此它的独立主题可能继续绘制；本仓库没有把后续合并版的实验代码回填到 0.6。
