## 🛠️ Development Guide

### Environment Setup

**Development Tools**:

- Android Studio Hedgehog (2023.1.1) or higher
- JDK 11 or higher
- Kotlin 1.9.x

**Clone Project**:

```bash
git clone https://github.com/your-repo/AutoXiaoer.git
cd AutoXiaoer
```

**Open Project**:

1. Launch Android Studio
2. Select "Open an existing project"
3. Select project root directory
4. Wait for Gradle sync to complete


### Build and Debug

**Debug Build**:

```bash
./gradlew assembleDebug
```

**Release Build**:

```bash
./gradlew assembleRelease
```

**Run Tests**:

```bash
./gradlew test
```

**Install to Device**:

```bash
./gradlew installDebug
```
