package com.flowmate.autoxiaoer.agent.tools

import com.flowmate.autoxiaoer.model.TokenUsage
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.json.JSONObject

/**
 * A capability that [com.flowmate.autoxiaoer.agent.LLMAgent] can advertise to the model
 * as an OpenAI-compatible function-call tool.
 *
 * Each tool owns its own JSON Schema and is responsible for executing the requested action.
 *
 * Tools should never throw — instead they return a [ToolResult] describing either:
 * - a continuation observation to feed back to the LLM, or
 * - a terminal task outcome (success / failure) to end the ReAct loop.
 *
 * Argument-validation errors are returned as `ToolResult.Continue` with an observation
 * explaining the problem so the model can retry.
 */
interface AgentTool {
    /** Stable unique tool name used in the `tool_calls[].function.name` field. */
    val name: String

    /** Natural-language description shown to the model in the `tools` request field. */
    val description: String

    /** JSON Schema object describing the tool's argument shape. */
    val parametersSchema: JsonObject

    /**
     * Executes the tool with the parsed [args].
     *
     * @param args The arguments object parsed from `tool_calls[].function.arguments`.
     * @param ctx  Shared dependencies and runtime state. Tools should consult
     *             [ToolContext.cancelRequested] / [ToolContext.pauseRequested] inside
     *             long-running loops.
     */
    suspend fun execute(args: JSONObject, ctx: ToolContext): ToolResult
}

/**
 * Outcome of a single [AgentTool.execute] invocation.
 */
sealed class ToolResult {
    /**
     * The ReAct loop should continue. [observation] is sent back to the model as a
     * `role: tool` message in the next request.
     *
     * @property observation Stringified result for the model to read.
     * @property subTaskMeta Sub-task execution details to attach to the planning round
     *   when this tool ran a [com.flowmate.autoxiaoer.agent.SubTask] via PhoneAgent.
     * @property brainTokenUsage Token usage from a BrainLLM call performed by this tool,
     *   only populated by request_brain.
     */
    data class Continue(
        val observation: String,
        val subTaskMeta: SubTaskMeta? = null,
        val brainTokenUsage: TokenUsage? = null,
        /** Screenshot to attach to the *next* round's model request for visual review. */
        val reviewScreenshotBase64: String? = null,
    ) : ToolResult()

    /**
     * The task has reached a terminal state. The main loop should record one final
     * planning round (using [observation] as its message) and stop.
     *
     * @property success Whether the task succeeded.
     * @property message Final summary returned to the caller.
     * @property observation Optional planning-round observation. Defaults to a short
     *   stringification of the termination.
     */
    data class Terminate(
        val success: Boolean,
        val message: String,
        val observation: String? = null,
    ) : ToolResult()
}

/**
 * Sub-task execution details captured by [com.flowmate.autoxiaoer.agent.tools.ExecuteSubtaskTool]
 * for inclusion in the surrounding planning round.
 */
data class SubTaskMeta(
    val subTaskDescription: String,
    val subTaskId: Int,
    val subTaskSuccess: Boolean,
    val subTaskStepCount: Int,
    val planningRoundTimestamp: Long,
)

// ──────────────────────────────────────────────────────────────────────────────
// Schema-building helpers used by tool implementations
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Returns an empty `{"type":"object","properties":{}}` schema for tools that take no arguments.
 */
internal val EmptyObjectSchema: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {}
}

/**
 * Builds a JSON-Schema object node.
 */
internal fun objectSchema(
    description: String? = null,
    required: List<String> = emptyList(),
    properties: JsonObjectBuilder.() -> Unit,
): JsonObject = buildJsonObject {
    put("type", "object")
    description?.let { put("description", it) }
    putJsonObject("properties") { properties() }
    if (required.isNotEmpty()) {
        putJsonArray("required") { required.forEach { add(it) } }
    }
}

internal fun JsonObjectBuilder.stringField(
    name: String,
    description: String,
    enum: List<String>? = null,
) {
    putJsonObject(name) {
        put("type", "string")
        put("description", description)
        if (enum != null) putJsonArray("enum") { enum.forEach { add(it) } }
    }
}

internal fun JsonObjectBuilder.integerField(
    name: String,
    description: String,
    minimum: Int? = null,
    maximum: Int? = null,
) {
    putJsonObject(name) {
        put("type", "integer")
        put("description", description)
        minimum?.let { put("minimum", it) }
        maximum?.let { put("maximum", it) }
    }
}

internal fun JsonObjectBuilder.booleanField(name: String, description: String) {
    putJsonObject(name) {
        put("type", "boolean")
        put("description", description)
    }
}

/**
 * Adds a nested object property with its own properties / required list.
 */
internal fun JsonObjectBuilder.objectField(
    name: String,
    description: String,
    required: List<String> = emptyList(),
    properties: JsonObjectBuilder.() -> Unit,
) {
    putJsonObject(name) {
        put("type", "object")
        put("description", description)
        putJsonObject("properties") { properties() }
        if (required.isNotEmpty()) {
            putJsonArray("required") { required.forEach { add(it) } }
        }
    }
}

/**
 * Adds a string-keyed string-valued map property (`{"type":"object","additionalProperties":{"type":"string"}}`).
 */
internal fun JsonObjectBuilder.stringMapField(name: String, description: String) {
    putJsonObject(name) {
        put("type", "object")
        put("description", description)
        putJsonObject("additionalProperties") { put("type", "string") }
    }
}
