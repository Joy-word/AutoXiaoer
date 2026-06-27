package com.flowmate.autoxiaoer.agent.tools

import com.flowmate.autoxiaoer.config.BehaviorContext
import com.flowmate.autoxiaoer.config.RelationshipContext
import com.flowmate.autoxiaoer.util.Logger
import org.json.JSONObject

/**
 * Reads the current relationship archive used by the expressor (BrainLLM).
 *
 * Mirrors the legacy `read_relationships` action.
 */
class ReadRelationshipsTool : AgentTool {
    override val name: String = NAME
    override val description: String =
        "Read the current relationship archive. The returned content can be passed as `facts` in a later request_brain call."
    override val parametersSchema = EmptyObjectSchema

    override suspend fun execute(args: JSONObject, ctx: ToolContext): ToolResult {
        val summary = RelationshipContext.getContext()
        Logger.i(TAG, "read_relationships (${summary.length} chars)")
        val observation = if (ctx.isEnglish) {
            "[Relationships]\n$summary\n\nYou can pass relevant entries as `facts` in `request_brain`."
        } else {
            "【人际关系】\n$summary\n\n可将其中相关信息作为 request_brain 的 facts 字段传入。"
        }
        return ToolResult.Continue(observation)
    }

    companion object {
        const val NAME = "read_relationships"
        private const val TAG = "ReadRelationshipsTool"
    }
}

/**
 * Updates the relationship archive used by the expressor (BrainLLM).
 *
 * Mirrors the legacy `update_relationships` action.
 */
class UpdateRelationshipsTool : AgentTool {
    override val name: String = NAME
    override val description: String =
        "Replace the entire relationship archive with the provided content. " +
            "Read the existing archive first, then write back an updated version."
    override val parametersSchema =
        objectSchema(required = listOf("content")) {
            stringField("content", "The full new content of the relationship archive (markdown).")
        }

    override suspend fun execute(args: JSONObject, ctx: ToolContext): ToolResult {
        val content = args.optString("content").ifBlank {
            return ToolResult.Continue(
                if (ctx.isEnglish) "update_relationships is missing the required `content` field. Please output the action again."
                else "update_relationships 缺少 content 字段，请重新输出。",
            )
        }
        RelationshipContext.saveNewVersion(content)
        Logger.i(TAG, "update_relationships (${content.length} chars)")
        val observation = if (ctx.isEnglish) {
            "[Relationships Updated] The archive has been saved. The expressor will use the new content on its next call."
        } else {
            "【人际关系已更新】档案已保存，表达者下次被调用时将自动使用新内容。"
        }
        return ToolResult.Continue(observation)
    }

    companion object {
        const val NAME = "update_relationships"
        private const val TAG = "UpdateRelationshipsTool"
    }
}

/**
 * Reads the current behaviour rules used by [com.flowmate.autoxiaoer.agent.LLMAgent].
 *
 * Mirrors the legacy `read_behavior_rules` action.
 */
class ReadBehaviorRulesTool : AgentTool {
    override val name: String = NAME
    override val description: String =
        "Read the current behaviour-rule preferences. Useful when the user gives feedback you may need to record."
    override val parametersSchema = EmptyObjectSchema

    override suspend fun execute(args: JSONObject, ctx: ToolContext): ToolResult {
        val rules = BehaviorContext.getContext()
        Logger.i(TAG, "read_behavior_rules (${rules.length} chars)")
        val observation = if (ctx.isEnglish) "[Behavior Rules]\n$rules" else "【行为准则】\n$rules"
        return ToolResult.Continue(observation)
    }

    companion object {
        const val NAME = "read_behavior_rules"
        private const val TAG = "ReadBehaviorRulesTool"
    }
}

/**
 * Updates the behaviour rules used by [com.flowmate.autoxiaoer.agent.LLMAgent].
 *
 * Mirrors the legacy `update_behavior_rules` action.
 */
class UpdateBehaviorRulesTool : AgentTool {
    override val name: String = NAME
    override val description: String =
        "Replace the entire behaviour-rule document with the provided content. " +
            "Read the existing rules first, then write back an updated version."
    override val parametersSchema =
        objectSchema(required = listOf("content")) {
            stringField("content", "The full new content of the behaviour rules (markdown).")
        }

    override suspend fun execute(args: JSONObject, ctx: ToolContext): ToolResult {
        val content = args.optString("content").ifBlank {
            return ToolResult.Continue(
                if (ctx.isEnglish) "update_behavior_rules is missing the required `content` field. Please output the action again."
                else "update_behavior_rules 缺少 content 字段，请重新输出。",
            )
        }
        BehaviorContext.saveNewVersion(content)
        Logger.i(TAG, "update_behavior_rules (${content.length} chars)")
        val observation = if (ctx.isEnglish) {
            "[Behavior Rules Updated] The rules have been saved and will take effect on the next task."
        } else {
            "【行为准则已更新】准则已保存，下次任务启动时将自动生效。"
        }
        return ToolResult.Continue(observation)
    }

    companion object {
        const val NAME = "update_behavior_rules"
        private const val TAG = "UpdateBehaviorRulesTool"
    }
}
