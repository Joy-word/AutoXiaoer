## 🛠️ 开发教程

### 环境准备

**开发工具**：

- Android Studio Hedgehog (2023.1.1) 或更高版本
- JDK 11 或更高版本
- Kotlin 1.9.x

**克隆项目**：

```bash
git clone https://github.com/Joy-word/AutoXiaoer.git
cd AutoXiaoer
```

**打开项目**：

1. 启动 Android Studio
2. 选择 "Open an existing project"
3. 选择项目根目录
4. 等待 Gradle 同步完成

### 构建和调试

**Debug 构建**：

```bash
./gradlew assembleDebug
```

**Release 构建**：

```bash
./gradlew assembleRelease
```

**Windows Release 打包脚本**：

将发行密钥库保存为 `app\release.keystore`，然后在项目根目录执行：

```powershell
.\release.ps1
# 指定生成密钥时使用的别名
.\release.ps1 -KeyAlias autoxiaoer
```

脚本会将 `versionCode` 加 1、将 `versionName` 的补丁号加 1（例如 `1.0.23` → `1.0.24`），然后构建 Release APK。成功后版本号保留在 `app/build.gradle.kts` 中；失败则恢复原文件。版本名称必须是数字形式的 `主版本.次版本.补丁版本`。

脚本优先使用当前进程的 `KEYSTORE_PASSWORD`、`KEY_PASSWORD`、`KEY_ALIAS` 环境变量，缺少时交互输入；密码输入隐藏，密钥密码留空则使用密钥库密码，别名默认 `autoxiaoer`。执行结束后恢复原有环境变量。密钥库缺失会直接报错，避免回退到 debug 签名。

APK 输出到 `app/build/outputs/apk/release/`。不要同时运行多个打包脚本，也不要在构建期间手动修改版本配置。密钥库和密码不得提交到仓库。

**运行测试**：

```bash
./gradlew test
```

**安装到设备**：

```bash
./gradlew installDebug
```
