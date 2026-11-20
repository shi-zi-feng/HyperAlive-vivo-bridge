# vivo alive 1.2-test · 基于 HyperAlive 1.0.4

面向 iQOO 12 / OriginOS 6 / Android 16 的**单应用适配测试版**，名称为 vivo alive，带桌面图标。不再需要另装原版 HyperAlive 或独立 0.6 兼容层。

**尚未完成真机验证。** 仅通过 89 项策略、32 项 JVM 框架注册检查及最终 APK/签名校验。旧 0.6 的显示验证结果不代表本版已验证；不用于循环重启设备救援。

## 改动

- 以 HyperAlive 1.0.4 为基础，包名更新为 `com.hyperalive.aod`；保留新版日志和应用前壁纸备份/恢复组件。
- 直接读取 vivo 熄屏开关，移除旧版全局 Settings 读取钩子；vivo 不注册上游的小米面板状态/渐暗/亮度渐变处理。
- 接回 vivo 窗口透出、模块时钟、锁屏/熄屏分离和 SystemUI/NightPearl 就绪交接；失败时回退。
- 移除“重启作用域”入口与框架/系统界面重启命令，应用壁纸不自动改 LSPosed 作用域。

## 使用和升级

仍需要已正常工作的 **Root + Android 16 可用的 LSPosed 兼容框架 + vivo 系统 AOD**。

- 作用域：系统框架 `android` / `system`、`com.android.systemui`、`com.vivo.nightpearl`、`com.hyperalive.aod`。
- 旧版 `com.qihang.aod` 的配置和照片不会自动迁移，先禁用旧模块并保留旧数据。不要与原版、旧合并版或 0.6 一起启用。
- 若已装作者原版 1.0.4，同包名但签名不同，不能直接覆盖；不要为了安装而贸然卸载/清除原数据。
- 在应用中选壁纸与熄屏时钟，系统中启用持续 AOD，再由使用者手动控制框架加载。没有自动重启。

[完整说明、源码和构建](https://github.com/shi-zi-feng/HyperAlive-vivo-bridge/tree/main/vivo-alive-1.2) · [上游 1.0.4](https://github.com/Askt-clot/HyperAlive/releases/tag/v1.0.4) · [LSPosed](https://github.com/LSPosed/LSPosed) / [Vector](https://github.com/JingMatrix/Vector)

附件为此前交付的原始 1.2 APK，未重新构建替换；源码构建使用自己的签名。

```text
05bb4abfa286390e31eba5cca200243613f633b66cb19dffd6d23b51c93be0aa  vivo-alive-1.2-test.apk
```

版本名 `1.2-test-upstream1.0.4`，versionCode `2026112001`，约 33.3 MB。签名私钥与设备原始资料不公开。0.6 历史发布继续保留。
