# AutoGLM 多模型架构研究笔记

> 整理自 Kiro 对话 - 2025年12月29日

## 目录

1. [当前提示词评估](#1-当前提示词评估)
2. [多模型方案调研](#2-多模型方案调研)
3. [方案对比分析](#3-方案对比分析)
4. [针对 AutoGLM 的改造建议](#4-针对-autoglm-的改造建议)
5. [可配置架构设计](#5-可配置架构设计)

---

## 1. 当前提示词评估

### 做得好的地方

- 输出格式明确（`<think>` + `<answer>`），便于解析
- 操作指令定义详细，每个 action 都有清晰的说明和示例
- 规则部分覆盖了很多边界情况（网络问题、滑动不生效、自定义键盘等）
- 中英文双版本，支持自定义 prompt

### 可以优化的地方

1. **规则过多且冗长** - 19 条规则太多了，模型可能难以全部遵循
2. **部分规则过于具体** - 比如"小红书总结要筛选图文笔记"、"购物车全选逻辑"这些是特定 app 的逻辑
3. **坐标系统重复说明** - 多个 action 都重复说明了 (0,0) 到 (999,999)
4. **英文版规则比中文版少** - 英文版只有 10 条规则，中文版有 19 条

### 改进建议：动态规则注入

把 app 特定逻辑抽离成可选的"任务上下文"：

```kotlin
object TaskContextRules {
    
    val SHOPPING_RULES = """
    - 购物车全选后再点击全选可以把状态设为全不选
    - 如果购物车里已有商品被选中，先点全选再取消全选
    """
    
    val FOOD_DELIVERY_RULES = """
    - 如果店铺购物车里已有其他商品，先清空再购买
    - 多个外卖尽量在同一店铺购买
    """
    
    val GAME_RULES = """
    - 战斗页面如果有自动战斗一定要开启
    """
}

fun getContextRules(task: String): String? {
    return when {
        task.contains("外卖") || task.contains("点餐") -> FOOD_DELIVERY_RULES
        task.contains("购物") || task.contains("购买") -> SHOPPING_RULES
        task.contains("游戏") -> GAME_RULES
        else -> null
    }
}
```

---

## 2. 多模型方案调研

### 2.1 Midscene 方案

来源：https://midscenejs.com/zh/model-strategy

**核心思路**：把 UI 自动化任务拆成三个独立的"意图"，每个意图用最适合的模型：

| 意图 | 职责 | 推荐模型 |
|------|------|----------|
| **Planning** | 任务规划、理解复杂指令 | GPT-5.1 等强推理模型 |
| **Insight** | 页面理解、数据提取、断言 | GPT-5.1 等 VQA 强的模型 |
| **Locate** (默认模型) | 元素定位、视觉 grounding | 豆包 Seed、千问 VL、UI-TARS |

### 2.2 Mobile-Agent-v3 方案

来源：阿里 X-PLUG 团队

**核心架构**：4 个专业化 Agent 协作

```
┌─────────────────────────────────────────────────────────────────┐
│                        Manager Agent                            │
│  - 任务理解与分解                                                │
│  - 协调其他 Agent                                               │
│  - 决定何时结束任务                                              │
└─────────────────────────────────────────────────────────────────┘
                              │
          ┌───────────────────┼───────────────────┐
          ▼                   ▼                   ▼
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│  Worker Agent   │  │ Reflector Agent │  │ Notetaker Agent │
│  - 执行具体操作  │  │  - 检查操作结果  │  │  - 记录关键信息  │
│  - 看截图定位    │  │  - 错误检测      │  │  - 跨 App 记忆   │
│  - 点击/滑动等   │  │  - 建议纠正策略  │  │  - 状态追踪      │
└─────────────────┘  └─────────────────┘  └─────────────────┘
```

**底层视觉模型**：GUI-Owl（基于 Qwen2.5-VL，7B~32B）

### 2.3 Hi-Agent 方案

来源：ICLR 2026 投稿论文

**核心思想**：分层架构，把"想什么"和"怎么做"分开

```
┌─────────────────────────────────────────────────────────────────┐
│                 High-Level Reasoning Model                      │
│                      (高层推理模型)                              │
│                                                                 │
│  输入: 任务描述 + 当前截图                                       │
│  输出: 子目标 (Subgoal)                                         │
│  例如: "点击搜索框" / "输入关键词" / "选择第一个结果"            │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                  Low-Level Action Model                         │
│                      (低层动作模型)                              │
│                                                                 │
│  输入: 子目标 + 当前截图                                         │
│  输出: 具体操作 (坐标/动作类型)                                  │
│  例如: do(action="Tap", element=[456, 123])                     │
└─────────────────────────────────────────────────────────────────┘
```

**关键创新**：Foresight Advantage Function
- 把多步决策拆成"单步子目标"序列
- 用低层模型的执行反馈来指导高层优化
- 不需要 Critic 网络，训练更稳定

**效果**：AitW 基准上达到 87.9% 成功率（AppAgent 只有 17.7%）

### 2.4 Mobile-Agent-v2 方案

**核心架构**：多 Agent 协作 + Memory 模块

```
Planning Agent → Decision Agent → Reflection Agent
                       ↓
                 Memory Module
```

---

## 3. 方案对比分析

### 效果对比（AndroidWorld 基准）

| 方案 | Agent 数量 | 成功率 |
|------|-----------|--------|
| Mobile-Agent-v1 | 1 | ~17% |
| Mobile-Agent-v2 | 2-3 | ~40% |
| Mobile-Agent-v3 | 4 | 73.3% |
| UI-TARS-2 | 1 | 73.3% |
| Hi-Agent | 2 | 87.9% |
| Step-GUI-8B | 1 | 80.2% |

### 复杂度 vs 收益

| 方案 | 复杂度 | 延迟 | 成本 | 适用场景 |
|------|--------|------|------|----------|
| 单模型 | 低 | 低 | 低 | 简单任务 |
| Midscene 三模型 | 中 | 中 | 中 | Web 表单 |
| Hi-Agent 双层 | 中 | 中 | 中 | 通用移动端 |
| Mobile-Agent-v3 | 高 | 高 | 高 | 复杂跨 App |

### Planning 出错的风险

多模型方案的核心风险是 Planning 可能出错：

1. **规划错了目标 App** - 用户想用美团，Planning 选了星巴克
2. **注入了错误的规则** - 错误分类任务类型
3. **步骤拆解不合理** - 实际 UI 和规划不符

**解决方案**：
- Planning 只做"软提示"，不做硬控制
- Planning 失败时降级到单模型
- 让 Action 模型有"纠错能力"

---

## 4. 针对 AutoGLM 的改造建议

### 如果只用 autoglm-phone

用同一个模型做分层，**技术上可以，但效果有限**：
- ✅ 可以通过不同 prompt 让它扮演不同角色
- ❌ 本质上还是同一个模型的能力
- ❌ 两次调用 = 两倍延迟 + 两倍成本
- ❌ 没有获得"专业分工"的收益

### 推荐的实施顺序

**Phase 1：优化单模型 prompt（推荐先做）**
- 精简核心规则到 5-6 条
- 场景规则动态注入
- 改动最小，风险最低

**Phase 2：加 Reflection 机制**
- 执行后检查是否成功
- 连续失败时触发反思
- 轻量改造

**Phase 3：真正分层（需要新模型）**
- High-Level: GPT-4o-mini / Claude Haiku（便宜，纯文本）
- Low-Level: autoglm-phone（现有模型）

---

## 5. 可配置架构设计

### 配置结构

```kotlin
data class AgentModeConfig(
    val mode: AgentMode,
    val highLevelModel: ModelConfig?,  // 高层模型配置（可选）
    val lowLevelModel: ModelConfig     // 低层模型配置（必须）
)

enum class AgentMode {
    SINGLE,      // 单模型：只用 lowLevelModel，完整 prompt
    HIERARCHICAL // 分层：highLevel 规划 + lowLevel 执行
}
```

### 用户界面设计

```
┌─────────────────────────────────────────┐
│  Agent 模式                             │
│  ○ 单模型（推荐，延迟低）                │
│  ○ 分层模型（复杂任务更准确）            │
├─────────────────────────────────────────┤
│  执行模型（必填）                        │
│  [autoglm-phone        ▼]               │
├─────────────────────────────────────────┤
│  规划模型（分层模式时启用）              │
│  [GPT-4o-mini          ▼]  [配置...]    │
└─────────────────────────────────────────┘
```

### 使用场景建议

| 场景 | 推荐模式 | 原因 |
|------|----------|------|
| 简单任务（发微信、打开 App） | 单模型 | 快，够用 |
| 复杂任务（跨 App、多步骤） | 分层 | 准确率高 |
| 没有额外 API Key | 单模型 | 只能这样 |
| 想省钱 | 单模型 | 一次调用 |

### 实现代码框架

```kotlin
class PhoneAgent(
    private val modeConfig: AgentModeConfig,
    // ... 其他依赖
) {
    private val highLevelClient: ModelClient? = 
        modeConfig.highLevelModel?.let { ModelClient(it) }
    
    private val lowLevelClient: ModelClient = 
        ModelClient(modeConfig.lowLevelModel)
    
    suspend fun executeStep(task: String?): StepResult {
        return when (modeConfig.mode) {
            AgentMode.SINGLE -> executeSingleMode(task)
            AgentMode.HIERARCHICAL -> executeHierarchicalMode(task)
        }
    }
    
    private suspend fun executeSingleMode(task: String?): StepResult {
        // 现有逻辑，完整 prompt
    }
    
    private suspend fun executeHierarchicalMode(task: String?): StepResult {
        val screenshot = captureScreenshot()
        
        // 1. 高层生成子目标
        val subgoal = highLevelClient!!.generateSubgoal(task, screenshot)
        
        if (subgoal == "任务完成") {
            return StepResult(success = true, finished = true)
        }
        
        // 2. 低层执行
        val action = lowLevelClient.executeSubgoal(subgoal, screenshot)
        
        // 3. 执行并返回结果
        return executeAction(action)
    }
}
```

---

## 参考资料

- [Midscene 模型策略](https://midscenejs.com/zh/model-strategy)
- [AndroidLens 论文](https://arxiv.org/html/2512.21302) - Mobile-Agent 系列对比
- [Hi-Agent 论文](https://openreview.net/forum?id=5a2cUmjoKa) - 分层架构
- [Mobile-Agent GitHub](https://github.com/X-PLUG/MobileAgent) - 阿里开源项目
