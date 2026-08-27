package com.flowmate.autoxiaoer.agent.tools

import com.flowmate.autoxiaoer.model.FunctionDto
import com.flowmate.autoxiaoer.model.ToolDto

/**
 * Holds the catalogue of [AgentTool] instances exposed to the model and converts them into
 * the OpenAI Function-Calling `tools` array.
 *
 * The order in which tools are advertised is the order they appear in [tools]; some models
 * weight earlier entries slightly higher when picking among ties, so list the most common
 * first.
 */
class ToolRegistry(val tools: List<AgentTool>) {

    private val byName: Map<String, AgentTool> = tools.associateBy { it.name }

    /** Looks up a tool by its declared [AgentTool.name]. Returns null when unknown. */
    fun find(name: String): AgentTool? = byName[name]

    /** Builds the OpenAI-format `tools` array advertised in the request body. */
    fun openAIToolDtos(): List<ToolDto> = tools.map { tool ->
        ToolDto(
            type = "function",
            function = FunctionDto(
                name = tool.name,
                description = tool.description,
                parameters = tool.parametersSchema,
            ),
        )
    }

    companion object {
        /**
         * Default registry containing every tool [com.flowmate.autoxiaoer.agent.LLMAgent] is
         * allowed to invoke, in roughly "most common first" order.
         */
        fun default(): ToolRegistry = ToolRegistry(
            listOf(
                ExecuteSubtaskTool(),
                RequestBrainTool(),
                RequestUserTool(),
                FinishTool(),
                ScheduleTaskTool(),
                QueryScheduledTasksTool(),
                UpdateScheduledTaskTool(),
                DeleteScheduledTaskTool(),
                ReadRelationshipsTool(),
                UpdateRelationshipsTool(),
                ReadBehaviorRulesTool(),
                UpdateBehaviorRulesTool(),
                ReadMemoryIndexTool(),
                ReadMemoryFileTool(),
                WriteMemoryFileTool(),
                DeleteMemoryFileTool(),
                QueryTaskHistoryTool(),
                GetTaskHistoryDetailTool(),
                WaitTool(),
                RandomNumberTool(),
            ),
        )

        /**
         * Returns a [ToolRegistry] that includes [RequestBrainTool] only when [brainEnabled]
         * is true. When the expressor is disabled or not configured, the `request_brain` tool
         * is excluded entirely — saving tokens and preventing accidental calls.
         */
        fun forBrainState(brainEnabled: Boolean): ToolRegistry {
            val tools = default().tools.toMutableList()
            if (!brainEnabled) {
                tools.removeAll {
                    it.name == RequestBrainTool.NAME ||
                        it.name == ReadRelationshipsTool.NAME ||
                        it.name == UpdateRelationshipsTool.NAME ||
                        it.name == RandomNumberTool.NAME
                }
            }
            return ToolRegistry(tools)
        }

        /**
         * Combines local tools (filtered by brainEnabled) with a snapshot of MCP tools.
         * MCP tools are appended after local tools so local ordering is preserved.
         */
        fun forRuntime(brainEnabled: Boolean, mcpTools: List<AgentTool>): ToolRegistry {
            val local = forBrainState(brainEnabled).tools
            // Deduplicate: drop any MCP tool whose namespaced name collides with a local name
            val localNames = local.map { it.name }.toHashSet()
            val filtered = mcpTools.filter { it.name !in localNames }
            return ToolRegistry(local + filtered)
        }
    }
}
