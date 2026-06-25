package com.flowmate.autoxiaoer.agent.tools

import org.json.JSONObject
import kotlin.random.Random

/**
 * Generates a random integer in the closed interval [min, max].
 *
 * Designed to help the model introduce randomness into decision-making:
 * the model can enumerate all candidate decisions, pick a range index
 * (e.g. 1..N), call this tool, and use the returned number to select one.
 */
class RandomNumberTool : AgentTool {
    override val name: String = NAME
    override val description: String =
        "Generate a random integer between `min` (inclusive) and `max` (inclusive). " +
            "Use this when you need to make a random decision among several options: " +
            "list all options, assign each an index starting from 1, call this tool with " +
            "min=1 and max=<number of options>, then pick the option whose index matches " +
            "the returned number."
    override val parametersSchema =
        objectSchema(required = listOf("min", "max")) {
            integerField(
                "min",
                "Lower bound of the random range (inclusive).",
            )
            integerField(
                "max",
                "Upper bound of the random range (inclusive). Must be >= min.",
            )
        }

    override suspend fun execute(args: JSONObject, ctx: ToolContext): ToolResult {
        val min = args.optInt("min", Int.MIN_VALUE)
        val max = args.optInt("max", Int.MIN_VALUE)

        if (min == Int.MIN_VALUE || max == Int.MIN_VALUE) {
            return ToolResult.Continue(
                if (ctx.isEnglish) "random_number requires both `min` and `max` integer arguments."
                else "random_number 需要同时提供 `min` 和 `max` 两个整数参数。",
            )
        }

        if (max < min) {
            return ToolResult.Continue(
                if (ctx.isEnglish) "random_number: `max` ($max) must be >= `min` ($min)."
                else "random_number：`max`（$max）必须大于或等于 `min`（$min）。",
            )
        }

        val result = Random.nextInt(min, max + 1)
        return ToolResult.Continue(
            if (ctx.isEnglish) "Random number in [$min, $max]: $result"
            else "在 [$min, $max] 范围内的随机数为：$result",
        )
    }

    companion object {
        const val NAME = "random_number"
    }
}
