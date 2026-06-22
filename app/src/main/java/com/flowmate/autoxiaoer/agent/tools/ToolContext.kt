package com.flowmate.autoxiaoer.agent.tools

import android.content.Context
import com.flowmate.autoxiaoer.agent.BrainLLM
import com.flowmate.autoxiaoer.agent.LLMAgentConfig
import com.flowmate.autoxiaoer.agent.LLMAgentListener
import com.flowmate.autoxiaoer.agent.PhoneAgent
import com.flowmate.autoxiaoer.history.HistoryManager
import com.flowmate.autoxiaoer.task.TriggerContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Shared state and dependencies passed to every [AgentTool] invocation.
 *
 * Acts as a thin lookup container — tools should not mutate it.
 *
 * @property config LLM-agent configuration (language, retry caps, etc.)
 * @property phoneAgent PhoneAgent used by execute_subtask to drive the device.
 * @property brainLLM Optional expressor; null when not configured.
 * @property historyManager Optional history sink for queries / detail lookup; null disables both.
 * @property appContext Application Context for accessing shared singletons (ScheduledTaskManager,
 *   ClawBotManager, FloatingWindowService, etc.). May be null in unit tests.
 * @property triggerContext How the current task was triggered. Affects request_user routing
 *   (ClawBot reply vs proactive push) and a few minor observation strings.
 * @property listener UI listener forwarded for sub-task lifecycle callbacks.
 * @property cancelRequested Set when the user cancels; long-running tools (e.g. wait) should
 *   poll this and return promptly.
 * @property pauseRequested Set when the user pauses; tools should suspend their progress until
 *   it clears or [cancelRequested] becomes true.
 */
class ToolContext(
    val config: LLMAgentConfig,
    val phoneAgent: PhoneAgent,
    val brainLLM: BrainLLM?,
    val historyManager: HistoryManager?,
    val appContext: Context?,
    val triggerContext: TriggerContext?,
    val listener: LLMAgentListener?,
    val cancelRequested: AtomicBoolean,
    val pauseRequested: AtomicBoolean,
) {
    /** Whether the current language preference is English. */
    val isEnglish: Boolean
        get() = config.language.lowercase().let { it == "en" || it == "english" }

    /**
     * 1-based planning round currently being executed by [com.flowmate.autoxiaoer.agent.LLMAgent].
     * Set by the main loop before invoking a tool so tools that produce sub-steps (notably
     * execute_subtask) can attribute them to the correct round in [HistoryManager].
     */
    var currentPlanningRound: Int? = null
}
