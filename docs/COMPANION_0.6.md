# HyperAlive vivo bridge · 0.6-test

为 **iQOO 12 / OriginOS 6 / Android 16** 制作的 HyperAlive 独立 LSPosed 兼容层。目标是让 HyperAlive 的熄屏壁纸和自定义时钟在系统 AOD 期间持续显示，解决本机出现的黑屏、仅在点亮时闪现、模块时钟与系统时钟重复等问题。

**这是附加模块，需要同时安装并启用原版 HyperAlive。单独安装本 APK 不会产生 Alive 壁纸。** 本仓库发布此前在一台 iQOO 12 上验证成功的 `0.6-test`，不是后来的单应用「vivo alive」合并分支，也不是 vivo 官方软件。

## 下载与必须搭配的组件

| 组件 | 用途与要求 | 地址 |
| --- | --- | --- |
| **本仓库 0.6-test APK** | OriginOS 兼容钩子、桌面状态页；不包含壁纸引擎 | [0.6 测试版发布页](https://github.com/shi-zi-feng/HyperAlive-vivo-bridge/releases/tag/v0.6-test) · [直接下载 APK](https://github.com/shi-zi-feng/HyperAlive-vivo-bridge/releases/download/v0.6-test/HyperAlive-vivo-bridge-0.6-test.apk) |
| **HyperAlive 原版 v1.0.3** | 提供 Alive 壁纸、三态动画、样式编辑器和 FlymeClockView | [上游仓库](https://github.com/Askt-clot/HyperAlive) · [v1.0.3 发布页](https://github.com/Askt-clot/HyperAlive/releases/tag/v1.0.3) · [原版 APK](https://github.com/Askt-clot/HyperAlive/releases/download/v1.0.3/HyperAlive-v1.0.3.apk) |
| **已正常工作的 Root + LSPosed 兼容框架** | 必须能在 Android 16 的系统框架、SystemUI 中加载传统 Xposed API 模块 | [LSPosed 项目](https://github.com/LSPosed/LSPosed) · [发布页](https://github.com/LSPosed/LSPosed/releases)；另有 [Vector（原 JingMatrix/LSPosed）](https://github.com/JingMatrix/Vector) / [发布页](https://github.com/JingMatrix/Vector/releases) |
| **系统熄屏显示 / AOD** | 在 OriginOS 中打开，并选择持续显示的系统模式；兼容层遵循系统的 AOD 时段和开关 | 手机「设置」内的熄屏显示选项 |

框架地址是项目来源，**不代表列表中的每个框架版本都在本机测试过**。请使用已确认适合自己 Android 16 / Root 环境的版本；本项目不提供 Root 漏洞、提权工具或固件刷写包。Root 和框架本身不是本兼容层安装器的一部分。

## 实际做了什么

壁纸、动画、时钟样式来自 [Askt-clot/HyperAlive](https://github.com/Askt-clot/HyperAlive)。本仓库只实现以下 vivo / iQOO 兼容工作：

1. **对齐熄屏开关。** 本机 OriginOS 使用 `Settings.System.screen_off_remind`，原模块读取 `Settings.Secure.doze_always_on`。仅当调用来自 HyperAlive 的 `StateCoordinator` 时映射读取值，不写全局设置，也不影响其他调用者。
2. **恢复 vivo 原生 AOD 电源流程。** 本机 vivo 的 `DisplayPowerState.setAodDisplayState` 需要框架处于 `OFF` 才接收 AOD 请求，原模块的 `OFF → DOZE` 快捷处理会与它冲突。兼容层通过高优先级、线程内的显示回调范围，仅绕过原模块这几处快捷处理；正常熄屏/唤醒指令仍保留。没有强制设置面板亮度或显示状态。
3. **让壁纸透过系统 AOD 显示。** 仅在主屏、当前使用 HyperAlive、系统 AOD 已开且已有睡眠标记时，透明化 vivo 的黑色覆盖窗口；对已确认的 SystemUI 遮罩清除背景，对精确匹配的 `AOD` / `AOD_ON_KEYGUARD` 窗口增加壁纸显示标志。唤醒或退出适用条件后恢复。
4. **补足环境模式能力。** 仅为 `com.qihang.aod` 的 `WallpaperInfo.supportsAmbientMode` 返回支持，原版 APK 不重签、不替换。
5. **在已验证的 SystemUI 路径中去重时钟。** 确认模块 AOD 时钟已附着、可见、尺寸有效且时间文字就绪后，跳过已识别的 vivo 原生时间/日期分支绘制；条件不满足时保留系统时钟，不改其 alpha、visibility 或触摸目标。
6. **提供桌面图标和只读状态页。** 显示版本、设备和系统 AOD 开关，并可打开原版 HyperAlive。页面无法证明 LSPosed 注入成功。

**本兼容层不刷写 `/system`、`/vendor`、`/product` 或 boot，不执行 remount，也不内置自动重启、卸载或 Root 提权。** 它通过 LSPosed 修改相关进程运行时行为，会影响 AOD 显示；“不改系统分区”不等于“不影响系统进程”。

实现细节见 [docs/IMPLEMENTATION.md](IMPLEMENTATION.md)。

## 安装和配置

1. 确认手机本身的系统 AOD 能正常显示，Root 与 LSPosed 兼容框架已正常工作。
2. 安装 **原版 HyperAlive v1.0.3** 和 **本兼容层 0.6-test**。不要用后来合并的 vivo alive 替代这里的原版，也不要把两条适配分支一起启用。
3. 在 LSPosed 中启用两款模块。针对本机的作用域如下：

   | 模块 | 本机使用的作用域 |
   | --- | --- |
   | HyperAlive 原版 | 系统框架 `android`、系统界面 `com.android.systemui`；原版其他推荐作用域按实际系统是否存在选择 |
   | HyperAlive vivo 兼容层 | 系统框架 `android`、系统界面 `com.android.systemui`、vivo 熄屏服务 `com.vivo.nightpearl` |

4. 在 **原版 HyperAlive** 中选择并应用壁纸，确认锁屏组件为 HyperAlive；设置希望使用的熄屏样式，并开启它的**自定义熄屏时钟**。0.6 的时钟去重以模块时钟准备就绪为前提，关闭该开关时不能期待它自动替换系统时钟。
5. 在 OriginOS 的系统设置中开启熄屏显示；希望常驻时，应选系统提供的持续显示模式。本模块不会把系统限时模式改为全天常驻。
6. 由使用者按自己已验证的 Root / 框架流程手动重新加载作用域或重启，再首次解锁。临时 Root 用户需确保重新加载后框架仍实际生效；仅显示“已启用”不等于已经注入。
7. 熄屏后确认物理屏幕是否持续显示，点亮检查锁屏恢复、指纹和时钟数量。若出现重叠，先看下方已知限制；不要把“有桌面图标”当作运行成功。

这是针对系统进程的测试模块，安装前应了解自己框架的禁用/救援方式。若异常，先禁用本兼容层，按自己的正常流程手动重载或重启；必要时同时禁用原版以区分来源。

## 验证结果与已知限制

2026-11-15，在一台 **iQOO 12（V2307A），OriginOS 6 / Android 16，系统版本 `PD2307C_A_16.2.20.3.W10`** 上，与原版 HyperAlive 1.0.3 配合，用户确认“模块样式可以持续显示”。固定采样在约 4 秒及 20 秒仍为 Asleep，面板处于原生 `DOZE_SUSPEND`；20 秒合成截图包含星球和一套模块时钟，时钟去重日志触发。

这证明当时那套壁纸、系统熄屏主题和时钟配置可工作，**不代表所有 vivo 机型、所有熄屏主题、长时间运行与耗电都已通过验证**。

- **0.6 的原生时钟去重只安装在 SystemUI 进程。** 虽然也对 NightPearl AOD 窗口做壁纸透明化，但没有把自定义时钟接入 NightPearl，也没有为其独立主题实现完整去重。系统切换到独立 NightPearl 主题时，可能仍出现系统主题与模块壁纸、锁屏内容叠加；这条路径不在 0.6 的成功验证范围内。
- 系统熄屏主题、关闭自定义时钟、换壁纸、唤醒/指纹转场等其他组合尚未完整回归。出现锁屏内容残留或多个时钟时，不应认定该配置已被 0.6 支持。
- 长期常驻、发热、耗电及防烧屏效果未量化验证。系统 AOD 的调度、时段、低电量和传感器策略仍由系统控制。
- 声明 minSdk 29 是构建参数，不代表 Android 10 至 16 或所有 OriginOS 版本均已适配。
- 本次发布保留已验证 APK 原文件。因此桌面状态页里仍有开发阶段的“尚待真机验证”等字样；验证状态以本 README 和 [脱敏验证记录](VALIDATION.md) 为准。

回退：在框架中禁用或卸载本兼容层，由使用者手动重载/重启。需要完整恢复系统壁纸时，还需在原版 HyperAlive 或系统设置中更换壁纸；禁用兼容层不会替你切换当前壁纸。

## 文件与校验

发布 APK 包名：`com.qihang.aod.vivobridge`，版本 `0.6-test`，versionCode `6`。

```text
81e0a1ddd3265f6b4daca3ad5b40df1c61ddb652881879ca575355062cc126eb  HyperAlive-vivo-bridge-0.6-test.apk
```

用于成功测试的原版 HyperAlive 1.0.3 APK SHA-256：

```text
55dca774ea69f3881db52ddcc60a491f626b8e761ec340cec031f4c8003a4946
```

本仓库不重新分发原版 APK、原版壁纸素材、vivo 系统 APK、用户照片或原始设备日志；原版请从作者发布页获取。上游若替换同名附件，应按自己的下载文件核对版本与校验值。

## 源码构建

完整独立构建说明见 [docs/BUILD.md](BUILD.md)。源码包含兼容层、资源、43 项纯 Java 策略检查以及不打包的最小 Xposed 编译声明，不依赖旁边的 HyperAlive 源码目录。

```sh
python build.py
```

自编译使用自己的签名密钥，通常不能覆盖本次公开的原测试 APK。原测试签名私钥不公开。

## 来源与授权

- 壁纸与时钟功能的原作者：[Askt-clot / HyperAlive](https://github.com/Askt-clot/HyperAlive)。感谢其提供原版模块。
- 本仓库的新增兼容层源码与构建文件采用 [MIT License](../LICENSE)。此许可不授予 HyperAlive、Xposed/LSPosed、vivo 系统组件或任何第三方素材的权利；这些项目按各自作者的授权处理。
- 本项目与 vivo、iQOO、魅族以及 HyperAlive 原作者无官方关联。

提交问题时，请附机型、系统完整版本、框架版本、原版版本、自定义熄屏时钟开关以及是否使用独立 NightPearl 主题。分享截图或日志前自行删除照片、通知、设备标识等隐私信息。
