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

**运行测试**：

```bash
./gradlew test
```

**安装到设备**：

```bash
./gradlew installDebug
```
