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

### Project Structure

```
app/src/main/java/com/flowmate/autoxiaoer/
├── action/                 # Action handling module
│   ├── ActionHandler.kt    # Action executor
│   ├── ActionParser.kt     # Action parser
│   └── AgentAction.kt      # Action data classes
├── agent/                  # Agent core module
│   ├── PhoneAgent.kt       # Phone Agent main class
│   └── AgentContext.kt     # Conversation context management
├── app/                    # App base module
│   ├── AppInfo.kt          # App info data class
│   ├── AppResolver.kt      # App name resolver
│   └── AutoGLMApplication.kt
├── config/                 # Configuration module
│   ├── I18n.kt             # Internationalization
│   └── SystemPrompts.kt    # System prompts
├── device/                 # Device operation module
│   └── DeviceExecutor.kt   # Device command executor
├── history/                # History module
│   ├── HistoryManager.kt   # History manager
│   ├── HistoryActivity.kt  # History UI
│   ├── HistoryDetailActivity.kt  # History detail UI
│   ├── HistoryDetailAdapter.kt   # History detail adapter
│   ├── HistoryModels.kt    # History data models
│   └── ScreenshotAnnotator.kt    # Screenshot annotator
├── home/                   # Home module
│   ├── TaskFragment.kt     # Task input screen
│   └── TemplateManagerDialog.kt  # Task template manager
├── input/                  # Input module
│   ├── TextInputManager.kt # Text input manager
│   ├── KeyboardHelper.kt   # Keyboard helper utility
│   └── AutoGLMKeyboardService.kt  # Built-in keyboard
├── model/                  # Model communication module
│   └── ModelClient.kt      # API client
├── notification/           # Notification trigger module
│   ├── NotificationTriggerRule.kt         # Trigger rule data class
│   ├── NotificationTriggerManager.kt      # Trigger rule manager
│   ├── AutoGLMNotificationListener.kt     # Notification listener service
│   ├── NotificationTriggerListDialog.kt   # Rule list dialog
│   ├── NotificationTriggerEditDialog.kt   # Rule edit dialog
│   └── InstalledApp.kt                    # Installed app data class
├── screenshot/             # Screenshot module
│   └── ScreenshotService.kt # Screenshot service
├── schedule/               # Scheduled task module
│   ├── ScheduledTask.kt    # Scheduled task data class
│   ├── ScheduledTaskManager.kt  # Scheduled task manager
│   ├── ScheduledTaskScheduler.kt # AlarmManager scheduler
│   ├── ScheduledTaskReceiver.kt  # Scheduled task broadcast receiver
│   ├── ScheduleTaskDialog.kt     # Create scheduled task dialog
│   ├── ScheduledTaskListDialog.kt # Scheduled task list dialog
│   └── BootReceiver.kt     # Boot receiver
├── settings/               # Settings module
│   ├── SettingsManager.kt  # Settings manager
│   └── SettingsFragment.kt # Settings UI
├── task/                   # Task execution module
│   └── TaskExecutionManager.kt  # Task execution manager
├── ui/                     # UI module
│   ├── FloatingWindowService.kt  # Floating window service
│   ├── FloatingWindowTileService.kt  # Quick tile service
│   ├── FloatingWindowToggleActivity.kt  # Floating window toggle
│   └── MainViewModel.kt    # Main screen ViewModel
├── util/                   # Utility module
│   ├── CoordinateConverter.kt    # Coordinate converter
│   ├── ErrorHandler.kt     # Error handler
│   ├── HumanizedSwipeGenerator.kt # Humanized swipe generator
│   ├── LogFileManager.kt   # Log file manager & export
│   ├── Logger.kt           # Logger utility
│   └── ScreenKeepAliveManager.kt # Screen keep-alive manager
├── BaseActivity.kt         # Base Activity
├── ComponentManager.kt     # Component manager
├── MainActivity.kt         # Main activity
└── UserService.kt          # Shizuku user service
```

### Core Module Description

**LLMAgent (agent/LLMAgent.kt)**

- Planning layer ("brain") of the dual-agent architecture
- Implements the ReAct loop: Think (call LLM to reason) → Act (dispatch sub-task to PhoneAgent) → Observe (feed result back into context)
- Breaks complex tasks into multiple sub-tasks, dispatched sequentially to PhoneAgent
- Supports `finish` and `request_user` as termination actions
- Configured via `LLMAgentConfig`, fully independent from PhoneAgent's model service

**LLMAgentContext (agent/LLMAgentContext.kt)**

- Manages the plain-text conversation context for LLMAgent
- No screenshots attached — handles system prompt, user input, and observation messages only
- Provides context reset; structurally consistent with PhoneAgentContext

**PhoneAgent (agent/PhoneAgent.kt)**

- Core Agent class, responsible for task execution flow
- Manages screenshot → model request → action execution loop
- Supports pause, resume, cancel operations

**ModelClient (model/ModelClient.kt)**

- Communicates with model API
- Supports SSE streaming responses
- Parses thinking process and action commands

**ActionHandler (action/ActionHandler.kt)**

- Executes various device operations
- Coordinates DeviceExecutor, TextInputManager and other components
- Manages floating window show/hide

**DeviceExecutor (device/DeviceExecutor.kt)**

- Executes shell commands via Shizuku
- Implements click, swipe, key press and other operations
- Supports humanized swipe trajectories

**ScreenshotService (screenshot/ScreenshotService.kt)**

- Captures screen and compresses to WebP
- Automatically hides floating window to avoid interference
- Supports sensitive page detection

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
