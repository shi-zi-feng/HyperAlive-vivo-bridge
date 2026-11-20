# vivo alive 1.2-test（基于 HyperAlive 1.0.4）

为 iQOO 12 / OriginOS 6 / Android 16 重新适配的单应用测试版。桌面名称为 **vivo alive**，含一个桌面入口、一个 LSPosed 模块，以及内置 vivo 熄屏适配。无需再安装独立兼容层。

上游正式版：[Askt-clot/HyperAlive v1.0.4](https://github.com/Askt-clot/HyperAlive/releases/tag/v1.0.4)，2026-11-16 发布。源码固定在该发布标签对应的提交 `5671c5202d2f2cfa73e852377a26d42443cce884`，不混用之后的 main 分支。上游作者及素材来源保留；本版本不是 vivo 官方软件。

**当前只完成电脑端构建与检查，尚未完成真机验证。** 移除框架软重启功能不代表已经排除所有启动故障。

## 本次改动

- 以 1.0.4 全部源码为基础，保留新版日志、照片草稿事务和应用前壁纸备份/恢复组件。
- 跟随新包名 `com.hyperalive.aod`，更新模块入口、壁纸识别、时钟类、配置 Provider 与跨进程时钟交接广播；旧包不会被误认为新模块持有壁纸。
- 在 `StateCoordinator` 内直接读取 vivo 的 `screen_off_remind`，不再对系统 `Settings.Secure.getInt` 安装全局钩子。未取得有效开关时，vivo 路径保守返回关闭。
- vivo / iQOO 跳过上游 `setScreenState`、`prepareColorFade` 与 Xiaomi 亮度渐变协调器的注册和调用；保留睡眠、超时及唤醒事件。面板电源状态和亮度由 vivo 原生 AOD 管理。
- 保留精确 AOD 窗口透出、SystemUI 遮罩处理、NightPearl 内模块时钟及锁屏/熄屏绘制隔离；唤醒或不再持有目标壁纸时恢复。
- 修正 NightPearl 交接时机：模块时钟真正附着、可见且时间文字就绪后，才通知 SystemUI 让出时钟宿主；异常、关闭自定义时钟或唤醒时撤回，避免空宿主压掉可用时钟。
- vivo 新配置默认启用模块熄屏时钟；已明确保存的关闭设置仍受尊重，锁屏时钟仍默认关闭。
- 保留一个桌面图标和 `vivo alive` 标题。**移除“重启作用域”菜单和框架/系统界面重启命令**；应用壁纸时也不再自动修改 LSPosed 作用域，交由用户在框架管理器中选择。
- 上游 224 个素材逐项以发布标签的 Git 对象哈希校验，其中 7 个发生变化的素材配置已更新。时钟、字体、壁纸媒体归原作者所有。

新增适配源码位于 `app/src/main/java/com/qihang/vivobridge`。这仅是兼容代码的 Java 命名空间，应用包名仍是 `com.hyperalive.aod`。对上游 11 个文件的修改见 `upstream-1.0.4-adaptation.patch`；其余上游源码保留原样。

## 安装测试前

APK：`vivo-alive-1.2-test.apk`，versionName `1.2-test-upstream1.0.4`，versionCode `2026112001`。

1. 先确认手机已经能正常启动、资料已妥善保存。不要把尚未完成真机验证的本版用于正在循环重启的设备救援。
2. 本版包名与旧的 vivo alive 1.0/1.1（`com.qihang.aod`）不同，**不是覆盖更新，不会自动迁移原设置和照片**。可以先保留旧应用数据，在框架管理器中禁用旧原版、旧合并版及独立兼容层，避免重复钩子；无需为了测试先卸载或清除旧数据。
3. 若已安装作者原版 HyperAlive 1.0.4，其包名与本版相同，签名不同，不能直接覆盖。不要为了覆盖而贸然卸载或清除原版数据；先确认需要保留的内容和备份方式，再选择测试方案。本仓库的构建脚本不会操作手机应用。
4. 需要已正常工作的 Root 与适合 Android 16 的 LSPosed 兼容框架。本项目不提供或执行漏洞提权。
5. 本版作用域：系统框架 `android`（管理器可能显示 `system`）、系统界面 `com.android.systemui`、vivo 熄屏服务 `com.vivo.nightpearl`、应用自身 `com.hyperalive.aod`。不要同时启用旧适配。
6. 在 vivo alive 应用内设置壁纸和熄屏时钟，在系统中启用持续熄屏显示；由使用者自行决定和操作框架加载。APK 内没有重启按钮，不自动重启。
7. 核验壁纸应用前备份、持续熄屏、只显示一套时钟、点亮/指纹/锁屏恢复、系统 AOD 关闭后的表现。需要反馈时使用上游新增日志页，先自行遮盖私人信息。

## 已验证与未验证

电脑端已经验证：

- 89 项壁纸归属、时钟交接、就绪失败回退、唤醒与新睡眠周期检查。
- 32 项 JVM 钩子注册检查：调用实际编译的 `SystemServerHook.install`，记录 vivo / iQOO 环境没有注册面板状态、渐暗和亮度渐变协调器，睡眠、唤醒与超时入口仍保留；以非 vivo 环境作为对照。测试使用模拟 Android / Xposed 类，不是真机。
- Android 资源、Java、DEX 编译、zipalign、APK v3 签名验证。
- 最终 APK 独立检查：应用名、新包名、单个桌面入口、新 Provider、单个模块入口、224 个素材哈希、未混入 Xposed 编译桩或 JVM 模拟类。
- 最终 DEX 不含 `ctl.restart` / `pkill -9` 命令，也没有重启作用域方法。
- 与之前 vivo alive 测试包使用同一份本地签名证书，但包名变化仍意味着独立应用数据。

尚未验证：本版在真实 OriginOS 上的注入、应用前壁纸备份/恢复、熄屏实际显示、所有主题和时钟组合、长时间运行及功耗。**电脑测试不能证明不会循环重启，也不能证明所有启动故障已经排除。** 备份前置校验失败时应保留日志排查，不绕过校验去改壁纸。

适配代码不刷写系统分区、boot 或 vbmeta，不做 remount。它仍通过运行时钩子影响系统框架与界面；上游应用会按用户操作写入应用/壁纸配置和 `/data` 中的临时辅助文件。“不刷分区”不等于“不会造成软件启动异常”。

## 构建与校验

先按 [SDK / JDK 工具说明](../docs/BUILD.md) 准备 Python 3.10+、JDK 17+、Android SDK 36 / build-tools 36.0.0 与 ECJ。可用 `BRIDGE_TOOLS_DIR` 指定工具目录；也支持 `JAVA_HOME`、`ANDROID_SDK_ROOT`、`ECJ_JAR`。APK 分析需要额外安装 `androguard`（构建本身不需要）。

```sh
python vivo-alive-1.2/build.py
python vivo-alive-1.2/verify_apk.py
```

构建会从上游固定提交下载 224 个素材，并逐项验证 Git 哈希；媒体不重复提交到本仓库。可用 `VIVO_ALIVE_ASSET_CACHE` 指定已有的素材目录，只有哈希一致的文件会被使用。源码、资源、编译声明、测试和适配补丁随本目录提供。构建只在电脑运行，不调用 ADB 或 Fastboot。

自编译输出：`build/vivo-alive-1.2-test-selfbuilt.apk`。默认使用在 `build/selfbuild.keystore` 创建的本地密钥；可通过 `VIVO_ALIVE_KEYSTORE`、`VIVO_ALIVE_KEY_ALIAS`、`VIVO_ALIVE_STORE_PASSWORD`、`VIVO_ALIVE_KEY_PASSWORD` 指定自有签名。**发布签名私钥不公开，自编译签名通常无法覆盖 Release APK。** 不承诺自编译包与发布包逐字节相同。

校验下载的正式测试附件：

```sh
python vivo-alive-1.2/verify_apk.py --apk /path/to/vivo-alive-1.2-test.apk --release
```

发布 APK 校验见 `SHA256SUMS.txt`；最初交付 APK 的脱敏电脑检查报告保存在 `validation/`，`device_tested` 明确为 `false`。上游来源记录位于 `upstream-provenance.json`、`upstream-file-hashes.json` 与 `upstream-assets.json`。

## 下载与依赖地址

- [vivo alive 1.2-test 预发布](https://github.com/shi-zi-feng/HyperAlive-vivo-bridge/releases/tag/v1.2-test)
- [直接下载 APK](https://github.com/shi-zi-feng/HyperAlive-vivo-bridge/releases/download/v1.2-test/vivo-alive-1.2-test.apk)
- [上游 HyperAlive 1.0.4](https://github.com/Askt-clot/HyperAlive/releases/tag/v1.0.4)：本版已经内置对应功能，不要同时启用原版。
- [LSPosed](https://github.com/LSPosed/LSPosed) / [Vector](https://github.com/JingMatrix/Vector)：需要能在目标 Android 16 环境正常工作的兼容框架，并非所有框架版本均已实测。

来源和许可范围见根目录 [NOTICE.md](../NOTICE.md)。0.6 历史版继续保留，不能与本版同时启用。
