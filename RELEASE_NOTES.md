# 0.6-test · HyperAlive vivo 兼容层

面向 iQOO 12 / OriginOS 6 / Android 16 的独立 LSPosed 附加模块，需搭配 **HyperAlive 原版 v1.0.3 + 已正常工作的 Root / LSPosed 兼容框架 + 系统 AOD**。

- [完整安装说明、作用域和已知限制](https://github.com/shi-zi-feng/HyperAlive-vivo-bridge#readme)
- [HyperAlive v1.0.3 发布页](https://github.com/Askt-clot/HyperAlive/releases/tag/v1.0.3)
- [原版 APK 下载](https://github.com/Askt-clot/HyperAlive/releases/download/v1.0.3/HyperAlive-v1.0.3.apk)
- [LSPosed](https://github.com/LSPosed/LSPosed) / [Vector（原 JingMatrix/LSPosed）](https://github.com/JingMatrix/Vector)；请选择适合自己 Android 16 / Root 环境的版本，非所有发行版均已实测。

本次适配：熄屏开关映射、vivo 原生 AOD 电源流程兼容、黑色遮罩/窗口背景透明化、环境模式能力补足，以及 SystemUI 中的时钟就绪检测与去重。提供独立桌面状态页。不修改系统分区、不重签原版 APK、不自动重启。

2026-11-15，一台 iQOO 12 上用户确认持续显示，约 4 秒和 20 秒采样均为 Asleep，20 秒截图只见一套模块时钟。**测试版；成功配置为 SystemUI 集成 AOD 路径。独立 NightPearl 主题仍可能叠加系统样式和锁屏内容，长期功耗与其他机型未验证。**

两款模块需同时启用；兼容层作用域为系统框架、SystemUI、`com.vivo.nightpearl`。在原版中应用壁纸并开启自定义熄屏时钟，在系统中启用持续 AOD，然后由使用者手动重新加载框架。不要与后来的单应用 vivo alive 合并分支混用。

APK 保留此前成功测试的原文件，未重新构建替换：

```text
81e0a1ddd3265f6b4daca3ad5b40df1c61ddb652881879ca575355062cc126eb  HyperAlive-vivo-bridge-0.6-test.apk
```

下载附件中的 APK 与 `SHA256SUMS.txt`。源代码和构建说明随仓库提供，原始设备日志、照片、系统 APK 和签名私钥不公开。
