package com.flowmate.autoxiaoer.agent.tools

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.flowmate.autoxiaoer.util.Logger
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject

/**
 * Idle-wait helper. Pauses the ReAct loop for a fixed duration while honouring
 * cancel / pause requests, and feeds elapsed time + battery level back to the model.
 *
 * Mirrors the legacy `wait` action including the low-battery guard at 14% or below.
 */
class WaitTool : AgentTool {
    override val name: String = NAME
    override val description: String =
        "Idle-wait for the given number of seconds with the screen kept on. " +
            "Use for timed polling or waiting until a specific moment. The screen stays on; " +
            "the loop is paused until the timer ends or the task is cancelled."
    override val parametersSchema =
        objectSchema(required = listOf("durationSeconds")) {
            integerField(
                "durationSeconds",
                "Wait duration in seconds (positive integer).",
                minimum = 1,
            )
        }

    override suspend fun execute(args: JSONObject, ctx: ToolContext): ToolResult = coroutineScope {
        val durationSeconds = args.optInt("durationSeconds", 0)
        if (durationSeconds <= 0) {
            return@coroutineScope ToolResult.Continue(
                if (ctx.isEnglish) "wait requires a positive integer `durationSeconds`."
                else "你输出的 wait 缺少有效的 durationSeconds 字段（需为正整数秒数），请重新输出。",
            )
        }

        val isEn = ctx.isEnglish
        val batteryPct = readBatteryLevel(ctx)
        if (batteryPct in 0..LOW_BATTERY_THRESHOLD) {
            val warning = if (isEn) {
                "[Battery Warning] Battery is at $batteryPct%, which is too low to sustain a ${durationSeconds}s wait. " +
                    "Please plug in the charger before continuing."
            } else {
                "【电量不足】当前电量 $batteryPct%，无法安全维持 ${durationSeconds} 秒的等待操作，请先插上电源再继续。"
            }
            Logger.w(TAG, "Battery too low for wait action: $batteryPct%")
            val observation = if (isEn) {
                "$warning\n\nDecide your next action: notify the user to charge, or abort the task."
            } else {
                "$warning\n\n请决定下一步操作：可以通过 request_user 提醒用户插电，或中止任务。"
            }
            return@coroutineScope ToolResult.Continue(observation)
        }

        Logger.i(TAG, "wait: idle-waiting ${durationSeconds}s (battery=$batteryPct%)")
        val startMs = System.currentTimeMillis()
        var remaining = durationSeconds

        while (remaining > 0 && isActive && !ctx.cancelRequested.get()) {
            while (ctx.pauseRequested.get() && !ctx.cancelRequested.get() && isActive) {
                delay(PAUSE_POLL_MS)
            }
            if (ctx.cancelRequested.get() || !isActive) break
            val sleepSec = minOf(remaining, POLL_CHUNK_SECONDS)
            delay(sleepSec * 1000L)
            remaining -= sleepSec
        }

        if (ctx.cancelRequested.get() || !isActive) {
            return@coroutineScope ToolResult.Terminate(
                success = false,
                message = if (isEn) "Task cancelled" else "任务已取消",
            )
        }

        val elapsed = ((System.currentTimeMillis() - startMs) / 1000).toInt()
        val batteryAfter = readBatteryLevel(ctx)
        val observation = if (isEn) {
            "[Wait Completed] Waited approximately ${elapsed}s with screen on. " +
                (if (batteryAfter >= 0) "Current battery: $batteryAfter%. " else "") +
                "Decide your next action."
        } else {
            "【等待完成】已保持亮屏等待约 ${elapsed} 秒。" +
                (if (batteryAfter >= 0) "当前电量：$batteryAfter%。" else "") +
                "请决定下一步操作。"
        }
        Logger.i(TAG, "wait done: elapsed=${elapsed}s battery=$batteryAfter%")
        ToolResult.Continue(observation)
    }

    private fun readBatteryLevel(ctx: ToolContext): Int {
        val appCtx = ctx.appContext ?: return -1
        val intent = appCtx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return -1
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        return if (level >= 0 && scale > 0) level * 100 / scale else -1
    }

    companion object {
        const val NAME = "wait"
        private const val TAG = "WaitTool"
        private const val LOW_BATTERY_THRESHOLD = 14
        private const val POLL_CHUNK_SECONDS = 30
        private const val PAUSE_POLL_MS = 200L
    }
}
