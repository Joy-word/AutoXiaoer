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

### 项目结构

```
app/src/main/java/com/flowmate/autoxiaoer/
├── action/                 # 动作处理模块
│   ├── ActionHandler.kt    # 动作执行器
│   ├── ActionParser.kt     # 动作解析器
│   └── AgentAction.kt      # 动作数据类
├── agent/                  # Agent 核心模块
│   ├── PhoneAgent.kt       # 手机 Agent 主类
│   └── AgentContext.kt     # 对话上下文管理
├── app/                    # 应用基础模块
│   ├── AppInfo.kt          # 应用信息数据类
│   ├── AppResolver.kt      # 应用名称解析
│   └── AutoGLMApplication.kt
├── config/                 # 配置模块
│   ├── I18n.kt             # 国际化
│   └── SystemPrompts.kt    # 系统提示词
├── device/                 # 设备操作模块
│   └── DeviceExecutor.kt   # 设备命令执行
├── history/                # 历史记录模块
│   ├── HistoryManager.kt   # 历史管理
│   ├── HistoryActivity.kt  # 历史界面
│   ├── HistoryDetailActivity.kt  # 历史详情界面
│   ├── HistoryDetailAdapter.kt   # 历史详情适配器
│   ├── HistoryModels.kt    # 历史数据模型
│   └── ScreenshotAnnotator.kt    # 截图标注工具
├── home/                   # 主页模块
│   ├── TaskFragment.kt     # 任务输入界面
│   └── TemplateManagerDialog.kt  # 任务模板管理
├── input/                  # 输入模块
│   ├── TextInputManager.kt # 文本输入管理
│   ├── KeyboardHelper.kt   # 键盘辅助工具
│   └── AutoGLMKeyboardService.kt  # 内置键盘
├── model/                  # 模型通信模块
│   └── ModelClient.kt      # API 客户端
├── notification/           # 通知触发模块
│   ├── NotificationTriggerRule.kt         # 触发规则数据类
│   ├── NotificationTriggerManager.kt      # 触发规则管理器
│   ├── AutoGLMNotificationListener.kt     # 通知监听服务
│   ├── NotificationTriggerListDialog.kt   # 规则列表对话框
│   ├── NotificationTriggerEditDialog.kt   # 规则编辑对话框
│   └── InstalledApp.kt                    # 已安装应用数据类
├── screenshot/             # 截图模块
│   └── ScreenshotService.kt # 截图服务
├── schedule/               # 定时任务模块
│   ├── ScheduledTask.kt    # 定时任务数据类
│   ├── ScheduledTaskManager.kt  # 定时任务管理器
│   ├── ScheduledTaskScheduler.kt # AlarmManager 调度器
│   ├── ScheduledTaskReceiver.kt  # 定时任务广播接收器
│   ├── ScheduleTaskDialog.kt     # 创建定时任务对话框
│   ├── ScheduledTaskListDialog.kt # 定时任务列表对话框
│   └── BootReceiver.kt     # 开机自启接收器
├── settings/               # 设置模块
│   ├── SettingsManager.kt  # 设置管理
│   └── SettingsFragment.kt # 设置界面
├── task/                   # 任务执行模块
│   └── TaskExecutionManager.kt  # 任务执行管理器
├── ui/                     # UI 模块
│   ├── FloatingWindowService.kt  # 悬浮窗服务
│   ├── FloatingWindowTileService.kt  # 快捷磁贴服务
│   ├── FloatingWindowToggleActivity.kt  # 悬浮窗切换
│   └── MainViewModel.kt    # 主界面 ViewModel
├── util/                   # 工具模块
│   ├── CoordinateConverter.kt    # 坐标转换
│   ├── ErrorHandler.kt     # 错误处理
│   ├── HumanizedSwipeGenerator.kt # 人性化滑动
│   ├── LogFileManager.kt   # 日志文件管理与导出
│   ├── Logger.kt           # 日志工具
│   └── ScreenKeepAliveManager.kt # 屏幕唤醒管理器
├── BaseActivity.kt         # 基础 Activity
├── ComponentManager.kt     # 组件管理器
├── MainActivity.kt         # 主界面
└── UserService.kt          # Shizuku 用户服务
```

### 核心模块说明

**LLMAgent (agent/LLMAgent.kt)**

- 双层 Agent 架构中的规划层（“大脑”）
- 实现 ReAct 循环：Think（调用 LLM 推理）→ Act（派发子任务给 PhoneAgent）→ Observe（将结果回馈入上下文）
- 将复杂任务拆分为多个子任务，逐一派发给 PhoneAgent 执行
- 支持 `finish` 和 `request_user` 多种终止方式
- 配置眼星 `LLMAgentConfig`，可独立指向与 Phone Agent 不同的模型服务

**LLMAgentContext (agent/LLMAgentContext.kt)**

- 管理 LLM Agent 的纯文本对话上下文
- 不携带截图，只处理系统提示词、用户输入与观察信息
- 提供上下文重置功能，与 PhoneAgentContext 结构一致

**PhoneAgent (agent/PhoneAgent.kt)**

- 核心 Agent 类，负责任务执行流程
- 管理截图 → 模型请求 → 动作执行的循环
- 支持暂停、继续、取消操作

**ModelClient (model/ModelClient.kt)**

- 与模型 API 通信
- 支持 SSE 流式响应
- 解析思考过程和动作指令

**ActionHandler (action/ActionHandler.kt)**

- 执行各种设备操作
- 协调 DeviceExecutor、TextInputManager 等组件
- 管理悬浮窗显示/隐藏

**DeviceExecutor (device/DeviceExecutor.kt)**

- 通过 Shizuku 执行 shell 命令
- 实现点击、滑动、按键等操作
- 支持人性化滑动轨迹

**ScreenshotService (screenshot/ScreenshotService.kt)**

- 截取屏幕并压缩为 WebP
- 自动隐藏悬浮窗避免干扰
- 支持敏感页面检测

**ScheduledTaskManager (schedule/ScheduledTaskManager.kt)**

- 管理定时任务的创建、读取、更新和删除
- 使用 SharedPreferences 持久化存储任务数据
- 通过 StateFlow 向 UI 层暴露任务列表变化
- 自动协调 AlarmManager 调度和屏幕唤醒管理

**ScheduledTaskScheduler (schedule/ScheduledTaskScheduler.kt)**

- 使用 AlarmManager 调度定时任务
- 支持精确定时，即使在 Doze 模式下也能触发
- 计算重复任务的下次执行时间（每天/工作日/每周）
- 管理任务的取消和重新调度

**ScreenKeepAliveManager (util/ScreenKeepAliveManager.kt)**

- 管理屏幕唤醒锁，确保任务执行期间屏幕保持点亮
- 为定时任务提供部分唤醒锁，保证后台定时触发
- 根据定时任务状态自动获取和释放唤醒锁

**TaskExecutionManager (task/TaskExecutionManager.kt)**

- 统一管理任务执行，协调 PhoneAgent 和各种服务
- 检查任务启动条件（Shizuku 状态、悬浮窗权限等）
- 处理定时任务和手动任务的统一调度

**NotificationTriggerManager (notification/NotificationTriggerManager.kt)**

- 管理通知触发规则的创建、读取、更新和删除
- 使用 SharedPreferences 持久化存储规则数据
- 通过 StateFlow 向 UI 层暴露规则列表变化
- 提供 `findMatchingRule` 方法供通知监听服务快速匹配

**AutoGLMNotificationListener (notification/AutoGLMNotificationListener.kt)**

- 继承自系统 `NotificationListenerService`，监听全局通知
- 收到通知时查询匹配的触发规则，若命中则调用 `TaskExecutionManager` 启动任务
- 若当前已有任务运行或启动条件不满足，则静默跳过，避免冲突

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
