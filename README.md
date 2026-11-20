# vivo alive / HyperAlive vivo 适配

面向 iQOO 12 / OriginOS 6 / Android 16 的 HyperAlive 熄屏适配。目标是在 vivo 原生 AOD 流程内持续显示模块壁纸和时钟，并区分熄屏、锁屏和亮屏内容。本项目不是 vivo 官方软件，与 HyperAlive 原作者无官方关联。

## 当前版本：vivo alive 1.2-test

**2026-11-20 更新：基于 HyperAlive 正式版 1.0.4 重新适配，单 App、单桌面图标，名称为 vivo alive。仅通过电脑端检查，尚未完成真机验证。**

- [下载 1.2 测试版](https://github.com/shi-zi-feng/HyperAlive-vivo-bridge/releases/tag/v1.2-test) · [直接下载 APK](https://github.com/shi-zi-feng/HyperAlive-vivo-bridge/releases/download/v1.2-test/vivo-alive-1.2-test.apk)
- [完整安装、作用域、构建与限制](vivo-alive-1.2/README.md)
- [适配补丁](vivo-alive-1.2/upstream-1.0.4-adaptation.patch) · [新增兼容源码](vivo-alive-1.2/app/src/main/java/com/qihang/vivobridge) · [电脑检查报告](vivo-alive-1.2/validation)

### 这次做了什么

1. 跟随上游新包名 `com.hyperalive.aod`，同步壁纸识别、配置 Provider、时钟类和跨进程时钟交接；保留上游新日志与壁纸备份/恢复组件。
2. 在模块内部直接读取 vivo 熄屏开关，移除旧适配对全局 Settings API 的读取钩子。
3. vivo / iQOO 不注册上游的小米面板状态、渐暗及亮度渐变处理；保留睡眠/唤醒事件，由 vivo 原生 AOD 管理面板状态和亮度。
4. 接回 SystemUI / NightPearl 的壁纸和时钟处理，只有模块时钟就绪才交接宿主，异常或退出时回退；锁屏与熄屏分开绘制。
5. 保留一个桌面图标，移除“重启作用域”入口及框架/系统界面重启命令，应用壁纸不再自动改 LSPosed 作用域。

**验证范围：89 项策略检查 + 32 项使用模拟框架的真实安装代码检查，以及资源/DEX/签名/最终 APK 校验。电脑检查不能证明物理熄屏效果、长期稳定性、功耗或不会循环重启。** 不要把本测试版当作无法开机设备的救援工具。

### 需要搭配什么

| 组件 | 1.2 的要求 | 地址 |
| --- | --- | --- |
| vivo alive 1.2-test | 已内置 HyperAlive 1.0.4 功能和兼容层；无需另装原版或 0.6 兼容层 | [本仓库预发布](https://github.com/shi-zi-feng/HyperAlive-vivo-bridge/releases/tag/v1.2-test) |
| Root + LSPosed 兼容框架 | 必须能在目标 Android 16 系统中正常注入；不提供漏洞提权工具 | [LSPosed](https://github.com/LSPosed/LSPosed) · [Vector（原 JingMatrix/LSPosed）](https://github.com/JingMatrix/Vector) |
| vivo 系统熄屏显示 | 系统中启用 AOD，需要常驻时选择系统提供的持续显示模式 | 手机系统设置 |
| 上游来源 | 1.0.4 正式标签，固定提交 `5671c5202d2f2cfa73e852377a26d42443cce884` | [HyperAlive 项目](https://github.com/Askt-clot/HyperAlive) · [1.0.4 发布页](https://github.com/Askt-clot/HyperAlive/releases/tag/v1.0.4) |

框架作用域：**系统框架 `android` / `system`、SystemUI `com.android.systemui`、vivo 熄屏服务 `com.vivo.nightpearl`、本应用 `com.hyperalive.aod`**。并非链接中的每个框架版本均已实测。

### 升级与数据

- 旧 vivo alive 1.0/1.1 和原版 1.0.3 使用 `com.qihang.aod`；新版是独立包，配置、照片不会自动迁移。先在框架管理器中禁用旧模块，保留旧应用数据，不需要为了测试先卸载或清数据。
- 作者原版 1.0.4 与本版包名相同、签名不同，不能直接覆盖。不要为了覆盖而贸然删除原数据，先确认备份与测试方案。
- 本版与 0.6 独立兼容层不能同时启用。加载及重启由使用者在自己已验证的框架流程中手动控制，APK 不自动重启。

## 历史版：0.6-test 独立兼容层

0.6 继续保留，不替换历史 APK。它是两 App 方案，必须搭配原版 HyperAlive 1.0.3；曾在一台 iQOO 12 上确认约 20 秒持续显示，但独立 NightPearl 主题仍可能叠加内容。不能把这份旧验证结果当作 1.2 已通过真机测试。

- [0.6 发布页](https://github.com/shi-zi-feng/HyperAlive-vivo-bridge/releases/tag/v0.6-test)
- [原版 1.0.3 下载](https://github.com/Askt-clot/HyperAlive/releases/tag/v1.0.3)
- [0.6 完整说明](docs/COMPANION_0.6.md) · [0.6 验证记录](docs/VALIDATION.md) · [0.6 构建说明](docs/BUILD.md)

根目录 `src/`、`res/`、`assets/`、`AndroidManifest.xml`、`build.py` 和 `tests/` 仍是 0.6 独立版；新增单应用版本位于 `vivo-alive-1.2/`，两套构建不会混合。

## 来源与公开范围

壁纸、动画、时钟、应用主体由 [Askt-clot/HyperAlive](https://github.com/Askt-clot/HyperAlive) 提供，vivo 兼容代码与改动在本仓库中明确标出。上游媒体在构建时从固定提交获取并验证哈希，不重复放入 Git 历史。

根目录 MIT 仅覆盖本项目新增兼容代码与构建/测试文件，不授予上游源码或第三方素材的权利，具体见 [NOTICE.md](NOTICE.md)。签名私钥、设备原始日志、系统 APK、个人照片不公开。

适配不刷写系统分区、boot 或 vbmeta，不做 remount；运行时钩子仍会影响系统进程，应用会按用户操作写入壁纸/应用配置。出现异常时先停用适配并保留资料，不以清除数据作为安装步骤。
