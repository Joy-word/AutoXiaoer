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
                QueryTaskHistoryTool(),
                GetTaskHistoryDetailTool(),
                WaitTool(),
            ),
        )
    }
}
