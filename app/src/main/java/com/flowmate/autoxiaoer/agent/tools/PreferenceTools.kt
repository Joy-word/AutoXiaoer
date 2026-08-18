package com.flowmate.autoxiaoer.agent.tools

import com.flowmate.autoxiaoer.config.BehaviorContext
import com.flowmate.autoxiaoer.config.RelationshipContext
import com.flowmate.autoxiaoer.util.Logger
import org.json.JSONObject
import java.util.UUID

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
        val summary = RelationshipContext.getContext(if (ctx.isEnglish) "en" else "cn")
        val token = "rel_" + UUID.randomUUID().toString().take(8)
        ctx.readTokens[RESOURCE_KEY] = token
        Logger.i(TAG, "read_relationships (${summary.length} chars), token=$token")
        val observation = if (ctx.isEnglish) {
            "[Relationships]\n$summary\n\nYou can pass relevant entries as `facts` in `request_brain`.\n[read_token: $token] — pass this token as `read_token` when calling update_relationships."
        } else {
            "【人际关系】\n$summary\n\n可将其中相关信息作为 request_brain 的 facts 字段传入。\n[read_token: $token] — 调用 update_relationships 时需将此 token 作为 read_token 字段传入。"
        }
        return ToolResult.Continue(observation)
    }

    companion object {
        const val NAME = "read_relationships"
        const val RESOURCE_KEY = "relationships"
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
            "This archive only records content related to you and your friends. " +
            "This is a full overwrite, not a merge, so update carefully. " +
            "Read the existing archive first, then write back an updated version."
    override val parametersSchema =
        objectSchema(required = listOf("content", "read_token")) {
            stringField("content", "The full new content of the relationship archive (markdown).")
            stringField("read_token", "The token returned by the most recent read_relationships call. Required to prevent blind overwrites.")
        }

    override suspend fun execute(args: JSONObject, ctx: ToolContext): ToolResult {
        val token = args.optString("read_token").ifBlank {
            return ToolResult.Continue(
                if (ctx.isEnglish)
                    "You must read the current relationship archive before modifying it. Please call read_relationships first to review the existing content, then call update_relationships with your changes."
                else
                    "修改人际关系档案前，请先调用 read_relationships 了解当前内容，再根据实际情况进行更新。",
            )
        }
        val expected = ctx.readTokens[ReadRelationshipsTool.RESOURCE_KEY]
        if (expected == null || token != expected) {
            return ToolResult.Continue(
                if (ctx.isEnglish)
                    "The relationship archive may have changed since you last read it. Please call read_relationships again to get the latest content before updating."
                else
                    "人际关系档案可能已发生变化。请重新调用 read_relationships 获取最新内容后再进行修改。",
            )
        }
        val content = args.optString("content").ifBlank {
            return ToolResult.Continue(
                if (ctx.isEnglish) "update_relationships is missing the required `content` field. Please output the action again."
                else "update_relationships 缺少 content 字段，请重新输出。",
            )
        }
        // 令牌用完即失效
        ctx.readTokens.remove(ReadRelationshipsTool.RESOURCE_KEY)
        RelationshipContext.saveNewVersion(content, if (ctx.isEnglish) "en" else "cn")
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
        val rules = BehaviorContext.getContext(if (ctx.isEnglish) "en" else "cn")
        val token = "bhv_" + UUID.randomUUID().toString().take(8)
        ctx.readTokens[RESOURCE_KEY] = token
        Logger.i(TAG, "read_behavior_rules (${rules.length} chars), token=$token")
        val observation = if (ctx.isEnglish)
            "[Behavior Rules]\n$rules\n[read_token: $token] — pass this token as `read_token` when calling update_behavior_rules."
        else
            "【行为准则】\n$rules\n[read_token: $token] — 调用 update_behavior_rules 时需将此 token 作为 read_token 字段传入。"
        return ToolResult.Continue(observation)
    }

    companion object {
        const val NAME = "read_behavior_rules"
        const val RESOURCE_KEY = "behavior_rules"
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
        objectSchema(required = listOf("content", "read_token")) {
            stringField("content", "The full new content of the behaviour rules (markdown).")
            stringField("read_token", "The token returned by the most recent read_behavior_rules call. Required to prevent blind overwrites.")
        }

    override suspend fun execute(args: JSONObject, ctx: ToolContext): ToolResult {
        val token = args.optString("read_token").ifBlank {
            return ToolResult.Continue(
                if (ctx.isEnglish)
                    "You must read the current behaviour rules before modifying them. Please call read_behavior_rules first to review the existing content, then call update_behavior_rules with your changes."
                else
                    "修改行为准则前，请先调用 read_behavior_rules 了解当前内容，再根据实际情况进行更新。",
            )
        }
        val expected = ctx.readTokens[ReadBehaviorRulesTool.RESOURCE_KEY]
        if (expected == null || token != expected) {
            return ToolResult.Continue(
                if (ctx.isEnglish)
                    "The behaviour rules may have changed since you last read them. Please call read_behavior_rules again to get the latest content before updating."
                else
                    "行为准则可能已发生变化。请重新调用 read_behavior_rules 获取最新内容后再进行修改。",
            )
        }
        val content = args.optString("content").ifBlank {
            return ToolResult.Continue(
                if (ctx.isEnglish) "update_behavior_rules is missing the required `content` field. Please output the action again."
                else "update_behavior_rules 缺少 content 字段，请重新输出。",
            )
        }
        // 令牌用完即失效
        ctx.readTokens.remove(ReadBehaviorRulesTool.RESOURCE_KEY)
        BehaviorContext.saveNewVersion(content, if (ctx.isEnglish) "en" else "cn")
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
