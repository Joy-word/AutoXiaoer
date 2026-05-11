# Auto Xiao'er

<div align="center">
<img src="screenshots/okxiaoer.png" width="120"/>

> "Kuang Kuang Kuang, I'm here!"

[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-7.0%2B-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-purple.svg)](https://kotlinlang.org)

English | [中文](README.md)

</div>

## 📸 Screenshots

<table>
  <tr>
    <td><img src="screenshots/screenshot_1.jpg" width="100%"/></td>
    <td><img src="screenshots/screenshot_2.jpg" width="100%"/></td>
    <td><img src="screenshots/screenshot_3.jpg" width="100%"/></td>
  </tr>
  <tr>
    <td><img src="screenshots/screenshot_4.jpg" width="100%"/></td>
    <td><img src="screenshots/screenshot_5.jpg" width="100%"/></td>
    <td><img src="screenshots/screenshot_6.jpg" width="100%"/></td>
  </tr>
</table>

---

## 📖 Introduction

Auto Xiao'er is a native Android application deeply modified from [AutoGLM For Android](https://github.com/Luokavin/AutoGLM-For-Android), drawing on OpenClaw ideas to let it operate your phone independently as your cyber companion.

> AutoGLM For Android is based on [Open-AutoGLM](https://github.com/zai-org/Open-AutoGLM) — it transforms the original computer + ADB phone automation into a standalone app running entirely on the phone.

**Key Features:**

- 🚀 **No Computer Required**: Runs directly on the phone without ADB connection
- 🎯 **Seamless Integration with Social Apps**: Vision-based operation works with any social app installed on your phone
- 🤖 **Dual-Agent Collaboration**: LLM + vision model for smarter task execution
- ⏰ **Scheduled Tasks**: Supports timed task execution with repeat modes, auto wake screen
- 🔔 **Notification Triggers**: Monitors specified app notifications and auto-triggers preset tasks
- 📶 **WeChat Remote Control**: Connect via WeChat QR code scan to control Xiao'er remotely
- 🔒 **Shizuku Permissions**: Obtains necessary system permissions through Shizuku
- 🪟 **Floating Window Interaction**: Floating window displays task execution progress in real-time
- 📱 **Native Experience**: Material Design, smooth native Android experience
- 🔌 **Multi-Model Support**: Compatible with any model API supporting OpenAI format and image understanding


## 📋 Features

### Core Features

- ✅ **Task Execution**: Input natural language task descriptions, AI automatically plans and executes
- ✅ **Screen Understanding**: Screenshot → Vision model analysis → Output action commands
- ✅ **Multiple Actions**: Click, swipe, long press, double tap, text input, launch apps, etc.
- ✅ **Task Control**: Pause, resume, cancel task execution
- ✅ **History**: Save task execution history, view details and screenshots
- ✅ **Scheduled Tasks**: Preset tasks to execute automatically at designated times, supporting one-time and repetitive tasks
- ✅ **Notification Triggers**: Monitor specific app notifications to automatically trigger corresponding tasks
- ✅ **WeChat Remote Control (ClawBot)**: Connect via WeChat QR code scan, send commands remotely and receive task execution results

### User Interface

- ✅ **Main Screen**: Task input, status display, quick actions
- ✅ **Floating Window**: Real-time display of execution steps, thinking process, action results
- ✅ **Settings Page**: Model configuration, Agent parameters, multi-profile management
- ✅ **History Page**: Task history list, detail view, screenshot annotations

### Advanced Features

- ✅ **Multi-Model Configuration**: Support saving multiple model configuration profiles for quick switching
- ✅ **Custom Prompts**: Support custom system prompts
- ✅ **Quick Tile**: Notification bar quick tile, fast access to floating window
- ✅ **Log Export**: Export debug logs with automatic sensitive data sanitization

## 📱 Requirements

- **Android Version**: Android 7.0 (API 24) or higher
- **Required App**: [Shizuku](https://shizuku.rikka.app/) (for system permissions)
- **Network**: Connection to model API service (supports any OpenAI-compatible vision model)
- **Permissions**:
  - Overlay permission (for floating window)
  - Network permission (for API communication)
  - Background running permission (for background task execution)
  - Shizuku permission (for system operations)
  - Notification listening permission (optional, for notification trigger feature)

## 🚀 Quick Start

### Step 1: Install and Activate Shizuku

Shizuku is the core dependency of this app, used to perform screen clicks, swipes, and other operations.

**Download and Install**

- [Google Play Download](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api)
- [GitHub Download](https://github.com/RikkaApps/Shizuku/releases)

**Activation Methods (Choose One)**

| Method           | Use Case                  | Persistence              |
| ---------------- | ------------------------- | ------------------------ |
| Wireless Debug   | Recommended, no PC needed | Re-pair after reboot     |
| ADB Connection   | When PC is available      | Re-execute after reboot  |
| Root Permission  | Rooted devices            | Permanent                |

**Wireless Debugging Activation Steps (Recommended)**

1. Connect to any WiFi network
2. Go to phone "Settings" → "Developer Options"
3. Enable "Wireless Debugging"
4. Tap "Pair device with pairing code"
5. Wait for Shizuku notification to appear, enter the pairing code in the notification
6. Open Shizuku and tap "Start", wait for it to complete
7. When Shizuku shows "Running", activation is successful

<table>
  <tr>
    <td><img src="screenshots/screenshot_7.jpg" width="100%"/></td>
    <td><img src="screenshots/screenshot_8.jpg" width="100%"/></td>
    <td><img src="screenshots/screenshot_9.jpg" width="100%"/></td>
  </tr>
  <tr>
    <td><img src="screenshots/screenshot_10.jpg" width="100%"/></td>
    <td><img src="screenshots/screenshot_11.jpg" width="100%"/></td>
    <td><img src="screenshots/screenshot_12.jpg" width="100%"/></td>
  </tr>
</table>

> 💡 **Tip**: If you can't find Developer Options, go to "About Phone" and tap "Build Number" multiple times to enable it.

### Step 2: Install Auto Xiao'er

1. Download the latest APK from [Releases Page](https://github.com/Joy-word/AutoXiaoer/releases)
2. Install the APK and open the app

### Step 3: Grant Required Permissions

After opening the app, grant the following permissions in order:

| Permission          | Purpose                    | Action                                           |
| ------------------- | -------------------------- | ------------------------------------------------ |
| Shizuku Permission  | Execute screen operations  | Tap "Authorize" → Always Allow                   |
| Overlay Permission  | Display task execution window | Tap "Authorize" → Enable toggle               |
| Keyboard Permission | Input text content         | Tap "Enable Keyboard" → Enable Xiao'er Keyboard  |

<table>
  <tr>
    <td><img src="screenshots/screenshot_13.jpg" width="100%"/></td>
    <td><img src="screenshots/screenshot_14.jpg" width="100%"/></td>
    <td><img src="screenshots/screenshot_15.jpg" width="100%"/></td>
  </tr>
</table>

> 💡 **Tip**: If overlay permission cannot be granted, go to app details page, tap "Menu (top right)" → Allow restricted settings, then try granting overlay permission again.

### Step 4: Configure Model Service

Go to "Settings" page and configure the AI model API.

This app uses a **dual-model, dual-agent architecture**:

| Role | Responsibility | Recommended Model |
| ---- | -------------- | ----------------- |
| **LLM Agent (Planning)** | Receives user tasks, performs high-level planning via ReAct loop, breaks complex tasks into sub-tasks | Pure text LLM (e.g. GLM-4, DeepSeek) |
| **Phone Agent (Execution)** | Awaits sub-tasks, analyzes screenshots and executes actions | Vision model with image understanding (e.g. autoglm-phone) |

> The two agents are configured independently and can point to different providers.

**Phone Agent Configuration (Vision Model)**

**Recommended Configuration (Zhipu BigModel)** 🎉 `autoglm-phone` model is currently FREE!

| Setting  | Value                                                                                   |
| -------- | --------------------------------------------------------------------------------------- |
| Base URL | `https://open.bigmodel.cn/api/paas/v4`                                                  |
| Model    | `autoglm-phone`                                                                         |
| API Key  | Get from [Zhipu AI Open Platform](https://open.bigmodel.cn/usercenter/proj-mgmt/apikeys)|

**Alternative Configuration (ModelScope)**

| Setting  | Value                                        |
| -------- | -------------------------------------------- |
| Base URL | `https://api-inference.modelscope.cn/v1`     |
| Model    | `ZhipuAI/AutoGLM-Phone-9B`                   |
| API Key  | Get from [ModelScope](https://modelscope.cn/)|

After configuration, tap "Test Connection" to verify the settings.

**LLM Agent Configuration (Planning LLM)**

Go to Settings → LLM Agent Configuration to set up the pure-text large language model used for planning:

| Setting             | Description                                                    |
| ------------------- | -------------------------------------------------------------- |
| Base URL            | OpenAI-compatible API endpoint                                 |
| Model               | Pure text model, e.g. `glm-4-plus`, `deepseek-chat`           |
| API Key             | API key for the corresponding service                          |
| Max Planning Steps  | Maximum ReAct iterations for the LLM loop, default 20         |
| Custom System Prompt| Overrides the built-in planning prompt to tune behaviour       |

> 💡 LLM Agent config is strictly independent from Phone Agent config — any OpenAI-compatible text model can be used.

<table>
  <tr>
    <td><img src="screenshots/screenshot_16.png" width="100%"/></td>
  </tr>
</table>

**Using Other Third-Party Models**:

Any model service can be used as long as it meets the following requirements:

1. **API Format Compatible**: Provides OpenAI-compatible `/chat/completions` endpoint
2. **Multi-modal Support**: Supports `image_url` format for image input
3. **Image Understanding**: Can analyze screenshots and understand UI elements

> ⚠️ **Note**: Non-AutoGLM models may require custom system prompts to output the correct action command format. You can customize system prompts in Settings → Advanced Settings.

### Step 5: Start Using

1. Enter a task description on the main screen, e.g., "Open WeChat and send a message to File Transfer: test"
2. Tap "Start Task" button
3. The floating window will automatically appear, showing execution progress
4. Watch the AI's thinking process and execution actions

---


## ⚠️ Security & Privacy Risks

Please read the following risks carefully before using this app:

### Safety Limits Are Prompt-Based

The app's safety restrictions (e.g. refusing to perform dangerous actions) are **implemented via AI model system prompts**, not hard-coded constraints. This means:

- Prompts can potentially be bypassed by carefully crafted task descriptions (i.e. "prompt injection" attacks)
- Different models vary in how strictly they follow the same prompt
- **Do not use this app for high-risk scenarios involving sensitive accounts, financial transactions, or private data**

### Model API Data Security

- All AI features in this app are powered **exclusively by third-party model APIs configured by the user**
- The app itself does not collect, upload, or store any user data or screenshots
- Screenshots taken during task execution are sent to your configured model service provider via the API you set up
- **Ensure you trust the model service provider you use, and review their privacy policy carefully**

### Usage Recommendations

- 🔒 Sensitive screens (payment pages, password fields, etc.) trigger system protection and appear as black screenshots
- 👀 When executing tasks involving sensitive operations, keep an eye on the screen and be ready to intervene manually
- 🔑 Do not include passwords, verification codes, or other sensitive information in task descriptions

---

## User Guide && FAQ
[User Guide && FAQ](Instructions_en.md)

---

## 📞 Contact

- Email: wxrachel@outlook.com


## ⭐ Star History

[![Star History Chart](https://api.star-history.com/svg?repos=Joy-word/AutoXiaoer&type=Date)](https://star-history.com/#Joy-word/AutoXiaoer&Date)


## 📄 License

This project is licensed under [MIT License](LICENSE).

## 🙏 Acknowledgments

- [AutoGLM-For-Android](https://github.com/Luokavin/AutoGLM-For-Android) - Luokavin's open-source project
- [Open-AutoGLM](https://github.com/zai-org/Open-AutoGLM) - Original open-source project
- [Shizuku](https://github.com/RikkaApps/Shizuku) - System permission framework
- [Zhipu AI](https://www.zhipuai.cn/) - AutoGLM model provider

---

<div align="center">

**If this project helps you, please give it a ⭐ Star!**

</div>
