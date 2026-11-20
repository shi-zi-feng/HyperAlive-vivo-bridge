# 来源与许可范围

## 本项目新增部分

根目录的 0.6 兼容层，以及 `vivo-alive-1.2/app/src/main/java/com/qihang/vivobridge/` 的新增兼容代码、本项目构建/校验/测试文件和新增图标，适用根目录 MIT License。

## HyperAlive 上游部分

`vivo-alive-1.2/app/src/main/java/com/hyperalive/aod/`、上游资源、编译期 API 声明和构建时获取的媒体来自 [Askt-clot/HyperAlive](https://github.com/Askt-clot/HyperAlive)，基于 `v1.0.4` / `5671c5202d2f2cfa73e852377a26d42443cce884`。

这些部分保留上游来源与作者权利，不因放入本仓库而被重新授予 MIT 许可。所作改动列于 `vivo-alive-1.2/upstream-1.0.4-adaptation.patch`；来源和 Git 哈希记录见同目录 `upstream-provenance.json`、`upstream-file-hashes.json` 与 `upstream-assets.json`。继续使用、分发上游源码或素材时，应核对其作者的授权。

上游媒体不重复放入本仓库 Git 源码树，在构建时从固定提交读取并逐项校验。Release 的单应用 APK 包含相应功能与素材；壁纸、字体和时钟设计不因本次适配归本项目所有。

本项目与 vivo、iQOO、魅族、LSPosed 以及 HyperAlive 原作者均无官方关联。
