# AutoGLM 日志记录参考表

本文档列出了项目中所有使用 `Logger` 记录日志的位置，包括日志级别、内容和敏感性分析。

## 敏感性标记说明

- 🔴 **敏感** - 包含可能泄露隐私的数据（URL、配置、用户数据等），导出时需脱敏
- 🟡 **半敏感** - 包含应用内部状态，可能间接泄露信息
- 🟢 **安全** - 不包含敏感信息

---

## 1. PhoneAgent.kt (agent/PhoneAgent.kt)

| 级别 | 日志内容 | 敏感性 |
|------|----------|--------|
| INFO | `Pause requested, current state: ${state}` | 🟢 |
| INFO | `Task paused at step $stepNumber, willRetryStep=$willRetry` | 🟢 |
| WARN | `Cannot pause: task is not running (state: ${state})` | 🟢 |
| INFO | `Resume requested, current state: ${state}` | 🟢 |
| INFO | `Task resumed from step $stepNumber` | 🟢 |
| WARN | `Cannot resume: task is not paused (state: ${state})` | 🟢 |
| WARN | `Task validation failed: empty or whitespace only` | 🟢 |
| WARN | `Task rejected: another task is already running` | 🟢 |
| INFO | `Task started: $task` (截断100字符) | 🟡 任务描述 |
| INFO | `Task cancelled by user at step $stepCount` | 🟢 |
| INFO | `Step returned paused, waiting for resume...` | 🟢 |
| INFO | `Task cancelled while paused` | 🟢 |
| INFO | `Resumed, retrying step...` | 🟢 |
| WARN | `Step $stepCount failed: $message` | 🟢 |
| INFO | `Task finished at step $stepCount: $message` | 🟢 |
| WARN | `Maximum steps (${maxSteps}) reached` | 🟢 |
| INFO | `Task completed/failed after $stepCount steps: $message` | 🟢 |
| INFO | `Task cancelled via coroutine cancellation` | 🟢 |
| ERROR | `Task execution error: $error` | 🟢 |
| INFO | `Step $stepNumber: $task` | 🟡 任务描述 |
| ERROR | `Agent context not initialized` | 🟢 |
| DEBUG | `Waiting ${delay}ms before screenshot...` | 🟢 |
| DEBUG | `Capturing screenshot...` | 🟢 |
| DEBUG | `Captured ${width}x${height}, sensitive=$isSensitive` | 🟢 |
| DEBUG | `Requesting model response...` | 🟢 |
| DEBUG | `Thinking: $thinking` (截断200字符) | 🟡 模型思考 |
| DEBUG | `Action: $action` | 🟡 模型动作 |
| WARN | `No action in model response, attempting retry...` | 🟢 |
| INFO | `Empty action retry $count/$max` | 🟢 |
| INFO | `Cancel requested, current state: ${state}` | 🟢 |
| INFO | `Task cancelled, state transitioned: $result` | 🟢 |
| INFO | `Resetting agent, current state: ${state}` | 🟢 |
| INFO | `Agent reset complete, state: ${state}` | 🟢 |

---

## 2. MainActivity.kt

| 级别 | 日志内容 | 敏感性 |
|------|----------|--------|
| INFO | `UserService connected` | 🟢 |
| INFO | `UserService disconnected` | 🟢 |
| WARN | `Shizuku binder died` | 🟢 |
| INFO | `ComponentManager initialized` | 🟢 |
| DEBUG | `onResume - checking for settings changes` | 🟢 |
| DEBUG | `stopTaskCallback invoked from floating window` | 🟢 |
| DEBUG | `resetAgentCallback invoked from floating window` | 🟢 |
| DEBUG | `pauseTaskCallback invoked from floating window` | 🟢 |
| DEBUG | `resumeTaskCallback invoked from floating window` | 🟢 |
| INFO | `onDestroy - cleaning up` | 🟢 |
| ERROR | `Error unbinding user service` | 🟢 |
| ERROR | `PhoneAgent not initialized` | 🟢 |
| DEBUG | `Agent not in IDLE state, resetting...` | 🟢 |
| WARN | `Task already running` | 🟢 |
| INFO | `Starting task from floating window: $task` | 🟡 任务描述 |
| ERROR | `Task execution error` | 🟢 |
| WARN | `Cannot initialize PhoneAgent: service not connected` | 🟢 |
| INFO | `PhoneAgent initialized successfully` | 🟢 |
| DEBUG | `startTask: Starting floating window service` | 🟢 |
| INFO | `Starting task: $taskDescription` | 🟡 任务描述 |
| INFO | `Task completed: $message` | 🟢 |
| WARN | `Task failed: $message` | 🟢 |
| ERROR | `Task error` | 🟢 |
| INFO | `Cancelling task` | 🟢 |
| INFO | `Pausing task` | 🟢 |
| INFO | `Resuming task` | 🟢 |
| DEBUG | `Button states updated: service=$s, agent=$a, text=$t, running=$r` | 🟢 |
| DEBUG | `onFloatingWindowRefreshNeeded called` | 🟢 |
| INFO | `Binding user service` | 🟢 |
| ERROR | `Failed to bind service` | 🟢 |

---

## 3. SettingsManager.kt (settings/SettingsManager.kt)

| 级别 | 日志内容 | 敏感性 |
|------|----------|--------|
| WARN | `Device does not support EncryptedSharedPreferences` | 🟢 |
| ERROR | `Failed to create encrypted prefs, using fallback` | 🟢 |
| DEBUG | `Loading model configuration` | 🟢 |
| DEBUG | `Saving model configuration: baseUrl=${url}, modelName=${name}` | 🔴 URL |
| DEBUG | `Loading agent configuration` | 🟢 |
| DEBUG | `Saving agent configuration: maxSteps=${steps}, language=${lang}` | 🟢 |
| INFO | `Clearing all settings` | 🟢 |
| INFO | `Migrated API Key to secure storage` | 🟢 |
| ERROR | `Failed to parse saved profiles` | 🟢 |
| DEBUG | `Saving profile: id=${id}, name=${displayName}` | 🔴 Profile名称 |
| DEBUG | `Deleting profile: id=$profileId` | 🟢 |
| ERROR | `Failed to parse task templates` | 🟢 |
| DEBUG | `Saving task template: id=${id}, name=${name}` | 🟡 模板名称 |
| DEBUG | `Deleting task template: id=$templateId` | 🟢 |
| DEBUG | `Imported dev profile: $name` | 🔴 Profile名称 |
| INFO | `Imported $count dev profiles` | 🟢 |
| ERROR | `Failed to import dev profiles` | 🟢 |

---

## 4. ModelClient.kt (model/ModelClient.kt)

| 级别 | 日志内容 | 敏感性 |
|------|----------|--------|
| DEBUG | `Cancelling current request` | 🟢 |
| DEBUG | `Request: POST $url` | 🔴 URL |
| DEBUG | `Preparing request with ${count} messages` | 🟢 |
| DEBUG | `Response: $statusCode (${duration}ms)` | 🟢 |
| DEBUG | `Response complete: ${length} chars, TTFT=${time}ms` | 🟢 |
| DEBUG | `First token received after ${time}ms` | 🟢 |
| VERBOSE | `Chunk parse error (ignored): ${message}` | 🟢 |
| ERROR | `Empty response received` | 🟢 |
| ERROR | `Request timeout` | 🟢 |
| ERROR | `Connection failed: ${message}` | 🟢 |
| ERROR | `Server error ${code}: ${message}` | 🟢 |
| ERROR | `Unknown error: ${message}` | 🟢 |
| DEBUG | `Request cancelled via coroutine cancellation` | 🟢 |
| ERROR | `Request failed: ${message}` | 🟢 |
| DEBUG | `Testing connection to: $url` | 🔴 URL |
| DEBUG | `Connection test successful, latency: ${latency}ms` | 🟢 |
| ERROR | `Connection test failed: Invalid API key` | 🟢 |
| ERROR | `Connection test failed: Model not found` | 🟢 |
| ERROR | `Connection test failed: ${code} - ${body}` | 🟢 |
| ERROR | `Connection test timeout` | 🟢 |
| ERROR | `Connection test failed: Unknown host` | 🟢 |
| ERROR | `Connection test failed: Connection refused` | 🟢 |

---

## 5. ScreenshotService.kt (screenshot/ScreenshotService.kt)

| 级别 | 日志内容 | 敏感性 |
|------|----------|--------|
| DEBUG | `Starting screenshot capture, window visible: $visible` | 🟢 |
| DEBUG | `Hiding floating window` | 🟢 |
| DEBUG | `Captured ${width}x${height}, sensitive=$isSensitive` | 🟢 |
| ERROR | `Screenshot error: $error` | 🟢 |
| DEBUG | `Restoring floating window` | 🟢 |
| DEBUG | `Executing screencap command` | 🟢 |
| WARN | `Failed to capture screenshot, returning fallback` | 🟢 |
| DEBUG | `PNG data captured: ${size} bytes` | 🟢 |
| WARN | `Failed to decode PNG, returning fallback` | 🟢 |
| DEBUG | `Scaled from ${w1}x${h1} to ${w2}x${h2}` | 🟢 |
| DEBUG | `Converted to WebP: ${size} bytes` | 🟢 |
| DEBUG | `Screenshot captured: ${w}x${h}, base64 length: ${len}` | 🟢 |
| ERROR | `Screenshot capture failed` | 🟢 |
| DEBUG | `Image already within limits: ${w}x${h}` | 🟢 |
| DEBUG | `Scaling with ratio $ratio: ${w1}x${h1} -> ${w2}x${h2}` | 🟢 |
| DEBUG | `Attempting screenshot capture` | 🟢 |
| DEBUG | `Screenshot capture took ${time}ms` | 🟢 |
| WARN | `Screenshot capture failed: $result` | 🟢 |
| WARN | `Base64 file not created or empty` | 🟢 |
| DEBUG | `Base64 file size: ${size} bytes` | 🟢 |
| DEBUG | `Reading $count chunks sequentially` | 🟢 |
| DEBUG | `Base64 read took ${time}ms, total length: ${len}` | 🟢 |
| WARN | `No base64 data read` | 🟢 |
| ERROR | `Failed to capture screenshot to bytes` | 🟢 |

---

## 6. FloatingWindowService.kt (ui/FloatingWindowService.kt)

| 级别 | 日志内容 | 敏感性 |
|------|----------|--------|
| DEBUG | `Service created` | 🟢 |
| DEBUG | `Service destroying` | 🟢 |
| DEBUG | `hide() called, isAttached=$attached` | 🟢 |
| DEBUG | `clearInputFocus: clearing focus and hiding keyboard` | 🟢 |
| DEBUG | `clearInputFocus: added FLAG_NOT_FOCUSABLE` | 🟢 |
| ERROR | `Error updating layout after clearing focus` | 🟢 |
| DEBUG | `show() called, isAttached=$attached, floatingView=$view` | 🟢 |
| DEBUG | `showAndBringToFront() called` | 🟢 |
| DEBUG | `updateStatus called with status: $status` | 🟢 |
| WARN | `updateStatus: floatingView is null!` | 🟢 |
| DEBUG | `reset() called - clearing steps and resetting to IDLE` | 🟢 |
| DEBUG | `reset() complete - startTaskCallback=$callback` | 🟢 |
| DEBUG | `setStopTaskCallback called` | 🟢 |
| DEBUG | `setPauseTaskCallback called` | 🟢 |
| DEBUG | `setResumeTaskCallback called` | 🟢 |
| DEBUG | `Confirmation requested: $message` | 🟢 |
| DEBUG | `Takeover requested: $message` | 🟢 |
| DEBUG | `Interact requested with options: $options` | 🟢 |
| DEBUG | `updateUIForStatus called with status: $status` | 🟢 |
| DEBUG | `Creating and showing floating window` | 🟢 |
| DEBUG | `Creating floating window view` | 🟢 |
| DEBUG | `Floating window view created` | 🟢 |
| DEBUG | `Touch outside window, clearing focus` | 🟢 |
| DEBUG | `taskInput touched (ACTION_DOWN)` | 🟢 |
| DEBUG | `Window focus changed: hasFocus=$hasFocus` | 🟢 |
| ERROR | `Error updating layout for focus` | 🟢 |
| DEBUG | `Resetting agent before starting new task` | 🟢 |
| DEBUG | `Starting task: $task, startTaskCallback=$callback` | 🟡 任务描述 |
| WARN | `Cannot add window: isAttached=$a, view=$v, params=$p` | 🟢 |
| DEBUG | `Adding window with params: x=$x, y=$y` | 🟢 |
| DEBUG | `Window added successfully` | 🟢 |
| DEBUG | `Window removed successfully` | 🟢 |
| ERROR | `Error adding window` | 🟢 |
| ERROR | `Error removing window` | 🟢 |

---

## 7. AutoGLMApplication.kt (app/AutoGLMApplication.kt)

| 级别 | 日志内容 | 敏感性 |
|------|----------|--------|
| DEBUG | `Activity started: ${className}, count: $count` | 🟢 |
| DEBUG | `App in foreground - hiding floating window` | 🟢 |
| DEBUG | `Activity stopped: ${className}, count: $count` | 🟢 |
| DEBUG | `App in background - showing floating window` | 🟢 |
| DEBUG | `Loaded custom Chinese system prompt` | 🟢 |
| DEBUG | `Loaded custom English system prompt` | 🟢 |
| INFO | `Imported $count dev profiles from assets` | 🟢 |
| DEBUG | `dev_profiles.json not found in assets` | 🟢 |
| ERROR | `Failed to import dev profiles` | 🟢 |

---

## 8. AppResolver.kt (app/AppResolver.kt)

| 级别 | 日志内容 | 敏感性 |
|------|----------|--------|
| INFO | `resolvePackageName called with: '$appName'` | 🟡 应用名 |
| INFO | `appName is blank, returning null` | 🟢 |
| INFO | `Normalized query: '$query'` | 🟡 应用名 |
| INFO | `appName contains '.', checking as package name` | 🟢 |
| INFO | `Found as package name: $appName` | 🟡 包名 |
| INFO | `Not a valid package name, continuing with fuzzy search` | 🟢 |
| INFO | `Found ${count} launchable apps` | 🟢 |
| DEBUG | `App: '${displayName}' -> ${packageName}` | 🟡 应用信息 |
| DEBUG | `Similarity '${displayName}': $score` | 🟡 应用名 |
| INFO | `Best match: '${name}' (${pkg}) with score $score` | 🟡 应用信息 |
| WARN | `No match found for '$appName'` | 🟡 应用名 |

---

## 9. ActionHandler.kt (action/ActionHandler.kt)

| 级别 | 日志内容 | 敏感性 |
|------|----------|--------|
| INFO | `Executing ${actionType} on ${w}x${h}` | 🟢 |
| DEBUG | `Action result: success=$success, message=$msg` | 🟢 |
| ERROR | `Action error: $error` | 🟢 |
| WARN | `Tap command failed: $result` | 🟢 |
| WARN | `Swipe command failed: $result` | 🟢 |
| DEBUG | `Launching app: ${app}` | 🟡 应用名 |
| DEBUG | `Using package name directly: ${app}` | 🟡 包名 |
| DEBUG | `Resolving app name: ${app}` | 🟡 应用名 |
| INFO | `Launching package: $packageName` | 🟡 包名 |
| WARN | `Launch failed for $packageName: $result` | 🟡 包名 |
| INFO | `Package not found for '${app}'` | 🟡 应用名 |
| DEBUG | `Listing all installed apps` | 🟢 |
| INFO | `Found ${count} installed apps` | 🟢 |
| WARN | `Back/Home/Volume/Power key press failed: $result` | 🟢 |
| WARN | `Long press command failed: $result` | 🟢 |
| WARN | `Double tap command failed: $result` | 🟢 |
| DEBUG | `Executing batch with ${count} steps, ${delay}ms delay` | 🟢 |
| WARN | `Skipping nested Batch action at step $index` | 🟢 |
| WARN | `Skipping Finish action in batch at step $index` | 🟢 |
| DEBUG | `Batch step ${i}/${total}: ${action}` | 🟢 |
| WARN | `Batch step ${i} failed: ${message}` | 🟢 |
| DEBUG | `Batch completed: ${count} steps, $status` | 🟢 |

---

## 10. ActionParser.kt (action/ActionParser.kt)

| 级别 | 日志内容 | 敏感性 |
|------|----------|--------|
| DEBUG | `Parsing response: ${response}` (截断100字符) | 🟡 模型响应 |
| WARN | `Unknown action format: $response` | 🟡 模型响应 |
| DEBUG | `Parsed finish action with message: ${msg}` (截断50字符) | 🟢 |
| WARN | `Unknown action type: $actionType` | 🟢 |

---

## 11. SettingsActivity.kt (settings/SettingsActivity.kt)

| 级别 | 日志内容 | 敏感性 |
|------|----------|--------|
| DEBUG | `SettingsActivity created` | 🟢 |
| DEBUG | `Loading current settings` | 🟢 |
| DEBUG | `Loading saved profiles` | 🟢 |
| DEBUG | `Saving current configuration as profile: $displayName` | 🔴 Profile名称 |
| DEBUG | `Deleting current profile: $id` | 🟢 |
| DEBUG | `Validating input` | 🟢 |
| INFO | `Saving settings` | 🟢 |
| INFO | `Resetting settings to defaults` | 🟢 |
| DEBUG | `Testing model connection` | 🟢 |
| DEBUG | `Connection test result: $result` | 🟢 |
| DEBUG | `Back button pressed` | 🟢 |
| DEBUG | `Showing edit prompt dialog for language: $lang` | 🟢 |
| INFO | `Exporting debug logs` | 🟢 |

---

## 12. HistoryManager.kt (history/HistoryManager.kt)

| 级别 | 日志内容 | 敏感性 |
|------|----------|--------|
| DEBUG | `Started recording task: ${taskId}` | 🟢 |
| ERROR | `Failed to save screenshot for step $stepNumber` | 🟢 |
| DEBUG | `Recorded step $stepNumber for task ${taskId}` | 🟢 |
| DEBUG | `Skipping empty task ${taskId}` | 🟢 |
| DEBUG | `Completed task ${taskId}, success=$success` | 🟢 |
| ERROR | `Failed to decode base64 to bitmap` | 🟢 |
| ERROR | `Failed to load task $taskId` | 🟢 |
| ERROR | `Failed to load history index` | 🟢 |

---

## 13. HistoryActivity.kt / HistoryDetailActivity.kt (history/)

| 级别 | 日志内容 | 敏感性 |
|------|----------|--------|
| DEBUG | `HistoryActivity created` | 🟢 |
| DEBUG | `Entered selection mode` | 🟢 |
| DEBUG | `Exited selection mode` | 🟢 |
| DEBUG | `Opening task detail: ${taskId}` | 🟢 |
| DEBUG | `Clearing all history` | 🟢 |
| DEBUG | `HistoryDetailActivity created for task: $taskId` | 🟢 |
| DEBUG | `Loaded task with ${count} steps` | 🟢 |
| DEBUG | `Copied prompt to clipboard` | 🟢 |
| DEBUG | `Deleting task: $id` | 🟢 |
| DEBUG | `Saving task as image` | 🟢 |
| DEBUG | `Image saved to gallery` | 🟢 |
| ERROR | `Failed to save image to gallery` | 🟢 |
| ERROR | `Error saving image` | 🟢 |
| DEBUG | `Sharing task as image` | 🟢 |
| ERROR | `Error sharing image` | 🟢 |

---

## 14. ComponentManager.kt

| 级别 | 日志内容 | 敏感性 |
|------|----------|--------|
| INFO | `UserService connected, initializing components` | 🟢 |
| INFO | `UserService disconnected, cleaning up components` | 🟢 |
| INFO | `All service-dependent components initialized` | 🟢 |
| INFO | `Service-dependent components cleaned up` | 🟢 |
| WARN | `Cannot reinitialize agent: UserService not connected` | 🟢 |
| WARN | `Cannot reinitialize agent: task is currently active` | 🟢 |
| INFO | `PhoneAgent reinitialized with new configuration` | 🟢 |
| INFO | `Cleaning up all components` | 🟢 |

---

## 15. KeyboardHelper.kt (input/KeyboardHelper.kt)

| 级别 | 日志内容 | 敏感性 |
|------|----------|--------|
| DEBUG | `Looking for keyboard: package=$pkg, service=$svc` | 🟢 |
| DEBUG | `Found IME: package=${pkg}, service=${svc}` | 🟢 |
| DEBUG | `AutoGLM Keyboard is enabled` | 🟢 |
| DEBUG | `AutoGLM Keyboard is not enabled` | 🟢 |
| DEBUG | `Opened input method settings` | 🟢 |
| ERROR | `Failed to open input method settings` | 🟢 |
| DEBUG | `Showed input method picker` | 🟢 |
| ERROR | `Failed to show input method picker` | 🟢 |

---

## 16. ErrorHandler.kt (util/ErrorHandler.kt)

| 级别 | 日志内容 | 敏感性 |
|------|----------|--------|
| ERROR | `Network error: $message` | 🟢 |
| ERROR | `Action error [$actionType]: $error` | 🟢 |
| ERROR | `Screenshot error: $error` | 🟢 |
| ERROR | `Permission error: $permission` | 🟢 |
| ERROR | `Shizuku error: $error` | 🟢 |
| ERROR | `Parsing error: $error, input: ${input}` (截断) | 🟡 模型响应 |
| ERROR | `Configuration error [$setting]: $error` | 🟢 |
| ERROR | `Unknown error: $error` | 🟢 |
| WARN | `App not found: $appName` | 🟡 应用名 |

---

## 17. 其他文件

### MainViewModel.kt (ui/MainViewModel.kt)
| 级别 | 日志内容 | 敏感性 |
|------|----------|--------|
| DEBUG | `updateShizukuStatus: $status` | 🟢 |
| DEBUG | `updateOverlayPermission: $hasPermission` | 🟢 |
| WARN | `Attempted to start task while another is running` | 🟢 |
| DEBUG | `Starting task: ${description}` (截断50字符) | 🟡 任务描述 |
| INFO | `Task completed successfully: $message` | 🟢 |
| WARN | `Task failed: $message` | 🟢 |
| ERROR | `Task error` | 🟢 |
| DEBUG | `Cancelling task` | 🟢 |
| DEBUG | `Step $stepNumber started` | 🟢 |
| DEBUG | `Action executed: ${action}` | 🟢 |
| INFO | `Task completed: $message` | 🟢 |
| ERROR | `Task failed: $error` | 🟢 |
| DEBUG | `Floating window refresh needed` | 🟢 |

### FloatingWindowToggleActivity.kt / FloatingWindowTileService.kt
| 级别 | 日志内容 | 敏感性 |
|------|----------|--------|
| DEBUG | `Toggle activity started, action: $action` | 🟢 |
| WARN | `No overlay permission` | 🟢 |
| DEBUG | `Tile clicked` | 🟢 |
| DEBUG | `Tile added` | 🟢 |
| DEBUG | `Tile removed` | 🟢 |

### UserService.kt
| 级别 | 日志内容 | 敏感性 |
|------|----------|--------|
| INFO | `destroy` | 🟢 |

### LogFileManager.kt (util/LogFileManager.kt)
| 级别 | 日志内容 | 敏感性 |
|------|----------|--------|
| ERROR | `Failed to export logs` | 🟢 |

---

## 敏感数据脱敏处理

导出日志时，`LogFileManager.sanitizeLogContent()` 会自动脱敏以下内容：

### 完全移除
- 应用列表（`=== All Launchable Apps ===` 相关内容）

### URL 相关
- 所有 URL 完全脱敏：`https://proxy.xxx.icu/v1/chat` → `***`
- 连接测试 URL：`Testing connection to: xxx` → `Testing connection to: ***`
- baseUrl 配置：`baseUrl=https://xxx.com` → `baseUrl=***`

### 配置/Profile 相关
- Profile 名称：`name=MyProfile` → `name=***`
- 任务模板名称：`Saving task template: name=xxx` → `name=***`
- 模型名称：`modelName=xxx` → `modelName=***`
- Dev profile 导入：`Imported dev profile: xxx` → `***`

### 任务描述
- `Task started: 给张三发微信` → `Task started: ***`
- `Step 1: 打开微信` → `Step 1: ***`
- `Starting task: xxx` → `Starting task: ***`
- `Starting task from floating window: xxx` → `***`

### 模型响应
- `Thinking: xxx` → `Thinking: ***`
- `Action: xxx` → `Action: ***`
- `Parsing response: xxx` → `Parsing response: ***`
- `Unknown action format: xxx` → `***`
- `Parsing error: input: xxx` → `***`

### 应用名/包名
- `resolvePackageName called with: '微信'` → `'***'`
- `App: '微信' -> com.tencent.mm` → `'***' -> ***`
- `Best match: '微信' (com.tencent.mm)` → `'***' (***)`
- `Launching app: 微信` → `Launching app: ***`
- `Launching package: com.tencent.mm` → `***`
- `App not found: xxx` → `***`
