# 独立源码构建

源码可以单独检出，不需要原版 HyperAlive 的源码或素材。最终 DEX 仅包含 `src/` 的兼容层；`stub/` 只有 Xposed API 的最小编译声明，不是框架实现，不打包到 APK。

## 工具要求

- Python 3.10+。
- Java 17+ 的 JDK（需要 `java` 与 `keytool`），设置 `JAVA_HOME` 或加入 PATH。
- [Android SDK 命令行工具](https://developer.android.com/tools/releases/cmdline-tools)：安装 `platforms;android-36` 和 `build-tools;36.0.0`。
- [Eclipse ECJ 3.40.0](https://repo.maven.apache.org/maven2/org/eclipse/jdt/ecj/3.40.0/ecj-3.40.0.jar)：下载为 `.tools/ecj.jar`，或设置 `ECJ_JAR`。

不把第三方 SDK、JDK 或编译器二进制放入 Git 仓库。参考 SDK 安装命令：

```sh
sdkmanager "platforms;android-36" "build-tools;36.0.0"
```

Android SDK 的许可接受与工具安装由构建者自行处理。

## 配置与执行

PowerShell 示例：

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk'
$env:ANDROID_SDK_ROOT = 'C:\path\to\android-sdk'
$env:ECJ_JAR = 'C:\path\to\ecj-3.40.0.jar'
python build.py
```

Linux / macOS 示例：

```sh
export JAVA_HOME=/path/to/jdk
export ANDROID_SDK_ROOT=/path/to/android-sdk
export ECJ_JAR=/path/to/ecj-3.40.0.jar
python3 build.py
```

也可用 `BRIDGE_TOOLS_DIR` 指定一个包含 `ecj.jar`、`sdk/` 的工具目录。默认读取本仓库的 `.tools/`，不会自动查找其他项目的源码。构建流程：

1. 编译仅作类路径的最小 API 声明。
2. 编译 0.6 的 6 个 Java 源文件。
3. 运行 `BridgePolicyTest`，共 43 项检查，覆盖厂商/组件匹配、主屏和睡眠条件、调用栈范围、窗口标志恢复、时钟/遮罩资源边界。
4. 仅将兼容层类编译为 DEX，打包资源与 Xposed 入口。
5. zipalign，使用本机密钥签名，再验证签名。

输出：`build/HyperAlive-vivo-bridge-0.6-test-selfbuilt.apk` 与 `build/build-report.json`。脚本不会调用 ADB、安装 APK、写手机设置或重启设备。

## 签名与可复现范围

未指定自有密钥时，脚本在 `build/local-debug.keystore` 创建一个本机调试密钥。它与公开的原测试 APK 签名不同，因此不能直接覆盖那个 APK；保留自己的密钥用于自编译版本后续更新。

自有密钥可通过 `BRIDGE_KEYSTORE`、`BRIDGE_KEY_ALIAS`、`BRIDGE_STORE_PASSWORD`、`BRIDGE_KEY_PASSWORD` 指定。不要将密钥或密码提交到 Git。仓库的忽略规则排除了这些常见构建产物，但不能代替人工检查。

公开发布附件是此前实际测试的原 APK，SHA-256 在 README 和发布页中提供。新构建脚本让源码可以独立构建，**不承诺自编译 ZIP / DEX / 签名与历史 APK 逐字节相同**；原签名密钥未公开。兼容层 `src/`、`res/`、`assets/`、manifest 和 43 项测试从成功基线保留，未混入 vivo alive 合并分支。
