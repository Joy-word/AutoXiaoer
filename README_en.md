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

## 📖 User Guide

### Basic Operations

**Start a Task**:

1. Enter task description on the main screen or floating window
2. Tap "Start" button
3. The app will automatically screenshot, analyze, and execute actions

**Control Tasks**:

| Button    | Function                        |
| --------- | ------------------------------- |
| ⏸️ Pause  | Pause after current step        |
| ▶️ Resume | Resume paused task              |
| ⏹️ Stop   | Cancel current task             |

**View History**:

1. Tap the "History" icon in the top right of main screen
2. View all executed tasks
3. Tap a task to view detailed steps and screenshots

**Scheduled Tasks**:

Xiao'er supports scheduled tasks — preset tasks to execute automatically at a specified time:

1. Enter task description on the main screen or floating window
2. Tap the "Schedule" button to open the schedule settings dialog
3. Choose execution time:
   - **Specific Time**: Select a date and time
   - **Delayed Execution**: Set a delay in hours and minutes
4. Choose repeat type:
   - **Once**: Auto-disable after one execution
   - **Daily**: Execute at the same time every day
   - **Weekdays**: Execute Monday through Friday
   - **Weekly**: Execute on the same day each week
5. Tap "Confirm" to save the scheduled task
6. Tap the 🕐 icon in the top right of main screen to view and manage all scheduled tasks
7. At the scheduled time, the app will automatically wake the screen and execute the task

**Scheduled Task Notes**:

- ⚠️ **Lock Screen Limitation**: If a screen lock password is set, scheduled tasks may not trigger while the screen is off — keep the phone unlocked before task execution
- 🔋 **Battery Optimization**: Disable battery optimization for Xiao'er in system settings to prevent the background process from being killed
- ⏰ **Precise Timing**: The app uses AlarmManager's exact alarm, ensuring on-time triggers even in Doze mode
- 🔁 **Repeat Tasks**: After each execution, the next run time is automatically calculated and rescheduled
- 📱 **Auto Start on Boot**: After device restart, all enabled scheduled tasks are automatically restored

**Notification Triggers**:

Xiao'er can automatically trigger preset tasks when a notification arrives from a specified app:

1. Go to "Settings" → "Notification Triggers"
2. Grant **notification listening permission** (System Settings → Notifications → Notification Access → Enable Xiao'er)
3. Tap "Add Rule", select the app to monitor
4. Enter the task description to execute when a notification arrives
5. Tap "Confirm" to save the rule
6. Once active, whenever that app sends a notification, Xiao'er will automatically execute the task

**Notification Trigger Notes**:

- 🔔 **Notification Permission**: Xiao'er cannot request this automatically — it must be granted manually
- ⚡ **Task Conflict**: If a task is already running, new notification triggers will be ignored without interrupting the running task
- 📦 **Package Name Matching**: Rules match by exact app package name; only the first enabled rule for each app will trigger
- 🔕 **Disable Rules**: Rules can be enabled or disabled individually at any time without deleting them

**WeChat Remote Control (ClawBot)**:

Using WeChat iLink Bot, you can send commands to your phone and receive results directly in WeChat — no need to open the app:

1. Go to "Settings" → "ClawBot WeChat Control"
2. Tap "Connect" and scan the QR code with WeChat
3. After scanning and confirming, the status changes to "Connected"
4. From then on, send any instruction to the Bot in WeChat and Xiao'er will execute it and reply the result
5. Tap "Disconnect" to unlink

**ClawBot Usage Notes**:

- 📶 **Background Keep-alive**: The polling service runs in the background long-term — disable battery optimization for Xiao'er to prevent it from being killed
- 🔄 **Auto Recovery**: After app restart, ClawBot connection is automatically restored — no need to re-scan
- ⚡ **Task Conflict**: If a task is already running, new incoming commands will be ignored without interrupting the running task
- 🔒 **Session Expiry**: If the WeChat Bot session expires, the app will notify you to re-scan


### Task Examples

**Social Communication**

```
Open WeChat, search for John and send message: Are you free tomorrow?
Open WeChat, check the latest Moments updates
```

**Shopping Search**

```
Open Taobao, search for wireless earphones, sort by sales
Open JD, search for phone cases, filter price under 50 yuan
```

**Food Delivery**

```
Open Meituan, search for nearby hotpot restaurants
Open Eleme, order a braised chicken rice
```

**Navigation**

```
Open Amap, navigate to the nearest subway station
Open Baidu Maps, search for nearby gas stations
```

**Video Entertainment**

```
Open TikTok, browse 5 videos
Open Bilibili, search for programming tutorials
```

**Scheduled Task Scenarios**

```
Every day at 7am: Open news app and check today's headlines
Weekdays at 8:30am: Open DingTalk and clock in
Every day at 10pm: Open Himalaya and play bedtime stories
Every Monday at 9am: Open notes app and check this week's to-dos
```

**Notification Trigger Scenarios**

```
Monitor "JD" notification → Open JD, check latest coupons
Monitor "WeChat" notification → Open WeChat, read and reply to unread messages
```

**WeChat Remote Control Scenarios (ClawBot)**

```
(Send to Bot in WeChat) Open WeChat and check the latest messages
(Send to Bot in WeChat) Take a photo and send it to me
(Send to Bot in WeChat) Set the phone to silent mode
(Send to Bot in WeChat) Open Alipay and check my balance
```

### Advanced Features

**Save Model Configuration**:

If you have multiple model APIs, save them as different profiles:

1. Go to "Settings" → "Model Configuration"
2. After configuring parameters, tap "Save Configuration"
3. Enter a profile name (e.g., Zhipu, OpenAI)
4. Switch between profiles quickly from the list

**Create Task Templates**:

Save frequently used tasks as templates for one-click execution:

1. Go to "Settings" → "Task Templates"
2. Tap "Add Template"
3. Enter template name and task description
4. Tap template button on main screen for quick selection

**Custom System Prompts**:

Optimize AI performance for specific scenarios:

1. Go to "Settings" → "Advanced Settings"
2. Edit system prompts
3. Add domain-specific instructions for enhancement

**Quick Tile**:

Add a quick tile to notification bar for fast floating window access:

1. Pull down notification bar, tap edit icon
2. Find "Xiao'er" tile
3. Drag it to the quick tile area

**Export Debug Logs**:

Export logs for troubleshooting when issues occur:

1. Go to "Settings" → "About"
2. Tap "Export Logs"
3. Logs will automatically sanitize sensitive information

## 🔧 FAQ

### Shizuku Related

**Q: Shizuku shows not running?**

A: Make sure Shizuku is installed and opened, follow the guide to activate. Wireless debugging is recommended.

**Q: Shizuku invalid after every restart?**

A: Wireless debugging requires re-pairing. Consider:

- Root method for permanent activation
- ADB method for activation

### Permission Related

**Q: Cannot grant overlay permission?**

A: Manual operation: System Settings → Apps → Xiao'er → Permissions → Enable "Display over other apps"

**Q: Cannot enable keyboard?**

A: Manual operation: System Settings → Language & Input → Manage Keyboards → Enable Xiao'er Keyboard

**Q: Cannot run in the background?**

A: Manual operation: System Settings → App Launch Manager → Xiao'er → Manage manually → Allow background activity

### Operation Related

**Q: Click action not working?**

A:

1. Check if Shizuku is running
2. Some systems require "USB debugging (Security settings)" enabled
3. Try restarting Shizuku

**Q: Text input failed?**

A:

1. Make sure Xiao'er Keyboard is enabled
2. Try manually switching input method once before executing task

**Q: Screenshot shows black screen?**

A: This is normal protection for sensitive pages (payment, password, etc.). The app will auto-detect and mark them.

### Model Related

**Q: API connection failed?**

A:

1. Check network connection
2. Verify API Key is correct
3. Verify Base URL format is correct (no trailing `/`)

**Q: Model response is slow?**

A:

1. Check network quality
2. Try switching to another model service
3. Adjust timeout in settings

### Scheduled Tasks Related

**Q: Scheduled task didn't trigger on time?**

A:

1. Check that battery optimization is disabled for Xiao'er in system settings
2. Make sure the app hasn't been killed by the system in the background
3. Some phones require "Background Running" and "Auto Start" permissions in settings
4. Check the task list to confirm the task is enabled

**Q: Scheduled task triggered but didn't execute?**

A:

1. If a screen lock password is set, keep the phone unlocked before task execution
2. Confirm Shizuku service is running (Shizuku needs to be re-activated after restart)
3. Check if another task is currently running — scheduled tasks won't interrupt a running task

**Q: Scheduled tasks lost after device restart?**

A: The app is configured to auto-start on boot to restore scheduled tasks, but some phone systems require manual authorization:

1. Go to System Settings → App Management → Xiao'er
2. Enable "Auto Start" or "Start on Boot" permission
3. Disable battery optimization
4. If Shizuku uses wireless debugging, re-pair after reboot

**Q: Repeat task only executed once?**

A:

1. Check if the task was accidentally set to "Once" mode
2. Check the task list to confirm the task is still enabled
3. If execution failed, the repeat task will auto-schedule for the next run time

### ClawBot WeChat Control

**Q: After scanning, status stays at "Waiting for scan"?**

A:

1. Confirm WeChat has already scanned the QR code
2. A confirmation page appears in WeChat — you must tap "Confirm" to complete binding
3. QR codes expire quickly — close the dialog and tap "Connect" again to get a new one

**Q: Connected but WeChat doesn't receive task results?**

A:

1. Make sure battery optimization is disabled for Xiao'er — the background polling service may have been killed
2. Check that the phone has a network connection
3. Verify in Settings that the ClawBot status still shows "Connected"

**Q: App shows "Session expired, please reconnect"?**

A: The WeChat iLink Bot session has a limited lifetime. Go to "Settings" → "ClawBot WeChat Control", tap "Connect" and scan the QR code again.

**Q: ClawBot disconnects after restarting the phone?**

A: The app automatically restores polling after restart — no re-scan needed. If it still fails, the session likely expired; re-scanning once fixes it. Disable battery optimization and enable auto-start for Xiao'er.

**Q: WeChat command was not executed?**

A:

1. Check that no other task is currently running (ClawBot commands don't interrupt running tasks)
2. Check that Shizuku service is running
3. Confirm overlay permission has been granted

### Notification Triggers Related

**Q: Notification trigger feature is not responding?**

A:

1. Confirm notification listening permission has been granted: System Settings → Notifications → Notification Access → Enable Xiao'er
2. Confirm the rule is enabled (toggle is on in the rule list)
3. Check if battery optimization is killing Xiao'er's background process
4. Some phones require "Background Running" and "Auto Start" permissions for Xiao'er

**Q: Notification received but task was not executed?**

A:

1. If a task is already running, new notification triggers are ignored — wait for the current task to finish
2. Check that Shizuku service is running
3. Confirm overlay permission has been granted
4. View logs (Settings → About → Export Logs) to investigate

**Q: How to stop notification triggers?**

A: Disable the corresponding rule's toggle in the rule list, or revoke Xiao'er's notification listening permission in system settings.

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

## 📄 License

This project is licensed under [MIT License](LICENSE).

## ⭐ Star History

[![Star History Chart](https://api.star-history.com/svg?repos=Joy-word/AutoXiaoer&type=Date)](https://star-history.com/#Joy-word/AutoXiaoer&Date)

## 🙏 Acknowledgments

- [AutoGLM-For-Android](https://github.com/Luokavin/AutoGLM-For-Android) - Luokavin's open-source project
- [Open-AutoGLM](https://github.com/zai-org/Open-AutoGLM) - Original open-source project
- [Shizuku](https://github.com/RikkaApps/Shizuku) - System permission framework
- [Zhipu AI](https://www.zhipuai.cn/) - AutoGLM model provider

## 📞 Contact

- Email: wxrachel@outlook.com

---

<div align="center">

**If this project helps you, please give it a ⭐ Star!**

</div>
