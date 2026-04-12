package com.flowmate.autoxiaoer.history

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.flowmate.autoxiaoer.R
import com.flowmate.autoxiaoer.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * RecyclerView adapter for history detail with header, LLM planning rounds, and phone agent steps.
 *
 * Items from [LLMPlanningRound] and [HistoryStep] are interleaved by timestamp so the
 * timeline reflects the actual execution order.
 *
 * @param historyManager Manager for loading screenshots
 * @param coroutineScope Scope for launching async operations
 */
class HistoryDetailAdapter(private val historyManager: HistoryManager, private val coroutineScope: CoroutineScope) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private var task: TaskHistory? = null

    /** Flattened, timestamp-sorted list of items after the header. */
    private val items = mutableListOf<Any>()

    private val loadedBitmaps = mutableMapOf<String, Bitmap>()
    private val loadingJobs = mutableMapOf<Int, Job>()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    /**
     * Sets the task to display.
     *
     * Rebuilds the interleaved item list sorted by timestamp.
     */
    fun setTask(task: TaskHistory) {
        this.task = task
        rebuildItems(task)
        Logger.d(TAG, "Set task with ${task.stepCount} steps and ${task.planningRoundCount} planning rounds")
        notifyDataSetChanged()
    }

    /**
     * Builds [items] by merging planning rounds and phone-agent steps sorted by timestamp.
     */
    private fun rebuildItems(task: TaskHistory) {
        items.clear()
        val merged = mutableListOf<Any>()
        merged.addAll(task.planningRounds)
        merged.addAll(task.steps)
        merged.sortWith(Comparator { a, b ->
            val ta = when (a) {
                is LLMPlanningRound -> a.timestamp
                is HistoryStep -> a.timestamp
                else -> 0L
            }
            val tb = when (b) {
                is LLMPlanningRound -> b.timestamp
                is HistoryStep -> b.timestamp
                else -> 0L
            }
            ta.compareTo(tb)
        })
        items.addAll(merged)
    }

    /**
     * Cleans up resources including cached bitmaps and pending jobs.
     */
    fun cleanup() {
        loadingJobs.values.forEach { it.cancel() }
        loadingJobs.clear()
        loadedBitmaps.values.forEach { if (!it.isRecycled) it.recycle() }
        loadedBitmaps.clear()
        Logger.d(TAG, "Cleaned up adapter resources")
    }

    override fun getItemViewType(position: Int): Int = when {
        position == 0 -> TYPE_HEADER
        else -> when (items[position - 1]) {
            is LLMPlanningRound -> TYPE_PLANNING_ROUND
            else -> TYPE_STEP
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderViewHolder(inflater.inflate(R.layout.item_history_header, parent, false))
            TYPE_PLANNING_ROUND -> PlanningRoundViewHolder(inflater.inflate(R.layout.item_history_planning_round, parent, false))
            else -> StepViewHolder(inflater.inflate(R.layout.item_history_step, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val currentTask = task ?: return
        when (holder) {
            is HeaderViewHolder -> holder.bind(currentTask)
            is PlanningRoundViewHolder -> {
                val item = items[position - 1]
                if (item is LLMPlanningRound) holder.bind(item)
            }
            is StepViewHolder -> {
                val item = items[position - 1]
                if (item is HistoryStep) holder.bind(item)
            }
        }
    }

    override fun getItemCount(): Int = 1 + items.size  // 1 header + merged items

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is StepViewHolder) {
            loadingJobs[holder.adapterPosition]?.cancel()
            loadingJobs.remove(holder.adapterPosition)
            holder.clearImage()
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // ViewHolder: Header
    // ──────────────────────────────────────────────────────────────────────────

    inner class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val taskDescription: TextView = itemView.findViewById(R.id.taskDescription)
        private val statusText: TextView = itemView.findViewById(R.id.statusText)
        private val infoText: TextView = itemView.findViewById(R.id.infoText)

        fun bind(task: TaskHistory) {
            taskDescription.text = task.taskDescription

            val context = itemView.context
            if (task.success) {
                statusText.text = context.getString(R.string.history_success)
                statusText.setTextColor(ContextCompat.getColor(context, R.color.status_success))
            } else {
                statusText.text = context.getString(R.string.history_failed)
                statusText.setTextColor(ContextCompat.getColor(context, R.color.status_error))
            }

            val duration = formatDuration(task.duration)
            val stepInfo = if (task.stepCount > 0) " · ${task.stepCount}步" else ""
            val roundInfo = if (task.planningRoundCount > 0) " · ${task.planningRoundCount}轮规划" else ""
            infoText.text = "${dateFormat.format(Date(task.startTime))}$stepInfo$roundInfo · $duration"
        }

        private fun formatDuration(ms: Long): String {
            val seconds = ms / 1000
            return when {
                seconds < 60 -> "${seconds}秒"
                seconds < 3600 -> "${seconds / 60}分${seconds % 60}秒"
                else -> "${seconds / 3600}时${(seconds % 3600) / 60}分"
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // ViewHolder: LLM Planning Round
    // ──────────────────────────────────────────────────────────────────────────

    inner class PlanningRoundViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val roundNumber: TextView = itemView.findViewById(R.id.roundNumber)
        private val actionTypeChip: TextView = itemView.findViewById(R.id.actionTypeChip)
        private val thinkingSection: LinearLayout = itemView.findViewById(R.id.thinkingSection)
        private val thinkingToggle: LinearLayout = itemView.findViewById(R.id.thinkingToggle)
        private val thinkingToggleIcon: ImageView = itemView.findViewById(R.id.thinkingToggleIcon)
        private val thinkingText: TextView = itemView.findViewById(R.id.thinkingText)
        private val subTaskSection: LinearLayout = itemView.findViewById(R.id.subTaskSection)
        private val subTaskDescription: TextView = itemView.findViewById(R.id.subTaskDescription)
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val observationSection: LinearLayout = itemView.findViewById(R.id.observationSection)
        private val observationText: TextView = itemView.findViewById(R.id.observationText)

        fun bind(round: LLMPlanningRound) {
            val context = itemView.context

            roundNumber.text = round.round.toString()

            // Action type chip
            val (chipLabel, chipColor) = actionChipInfo(context, round.actionType)
            actionTypeChip.text = chipLabel
            actionTypeChip.backgroundTintList = android.content.res.ColorStateList.valueOf(chipColor)

            // Thinking (collapsible)
            if (round.thinking.isNotBlank()) {
                thinkingSection.visibility = View.VISIBLE
                thinkingText.text = round.thinking

                var expanded = false
                thinkingToggleIcon.setImageResource(R.drawable.ic_expand_more)
                thinkingText.visibility = View.GONE

                thinkingToggle.setOnClickListener {
                    expanded = !expanded
                    thinkingText.visibility = if (expanded) View.VISIBLE else View.GONE
                    thinkingToggleIcon.setImageResource(
                        if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more
                    )
                }
            } else {
                thinkingSection.visibility = View.GONE
            }

            // Sub-task description
            if (!round.subTaskDescription.isNullOrBlank()) {
                subTaskSection.visibility = View.VISIBLE
                subTaskDescription.text = round.subTaskDescription
            } else {
                subTaskSection.visibility = View.GONE
            }

            // Message (finish / request_user / schedule operations)
            if (!round.message.isNullOrBlank()) {
                messageText.visibility = View.VISIBLE
                messageText.text = round.message
            } else {
                messageText.visibility = View.GONE
            }

            // Observation
            if (!round.observation.isNullOrBlank()) {
                observationSection.visibility = View.VISIBLE
                observationText.text = round.observation
            } else {
                observationSection.visibility = View.GONE
            }
        }

        /**
         * Maps an action type string to a display label and background color.
         */
        private fun actionChipInfo(context: android.content.Context, actionType: String): Pair<String, Int> {
            return when (actionType) {
                "execute_subtask" -> "执行子任务" to ContextCompat.getColor(context, R.color.primary)
                "finish" -> "完成" to ContextCompat.getColor(context, R.color.status_success)
                "request_user" -> "需要介入" to ContextCompat.getColor(context, R.color.status_paused)
                "schedule_task" -> "记录日程" to ContextCompat.getColor(context, R.color.step_llm_agent_action)
                "query_scheduled_tasks" -> "查询日程" to ContextCompat.getColor(context, R.color.step_llm_agent_action)
                "update_scheduled_task" -> "更新日程" to ContextCompat.getColor(context, R.color.step_llm_agent_action)
                "delete_scheduled_task" -> "删除日程" to ContextCompat.getColor(context, R.color.status_error)
                else -> actionType to ContextCompat.getColor(context, R.color.icon_secondary)
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // ViewHolder: PhoneAgent Step
    // ──────────────────────────────────────────────────────────────────────────

    inner class StepViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val stepNumber: TextView = itemView.findViewById(R.id.stepNumber)
        private val actionDescription: TextView = itemView.findViewById(R.id.actionDescription)
        private val statusIcon: ImageView = itemView.findViewById(R.id.statusIcon)
        private val thinkingSection: LinearLayout = itemView.findViewById(R.id.thinkingSection)
        private val thinkingText: TextView = itemView.findViewById(R.id.thinkingText)
        private val screenshotSection: LinearLayout = itemView.findViewById(R.id.screenshotSection)
        private val screenshotImage: ImageView = itemView.findViewById(R.id.screenshotImage)
        private val btnOriginal: MaterialButton = itemView.findViewById(R.id.btnOriginal)
        private val btnAnnotated: MaterialButton = itemView.findViewById(R.id.btnAnnotated)
        private val messageText: TextView = itemView.findViewById(R.id.messageText)

        private var currentStep: HistoryStep? = null

        fun bind(step: HistoryStep) {
            currentStep = step

            stepNumber.text = step.stepNumber.toString()
            actionDescription.text = step.actionDescription

            val context = itemView.context
            if (step.success) {
                statusIcon.setImageResource(R.drawable.ic_check_circle)
                statusIcon.setColorFilter(ContextCompat.getColor(context, R.color.status_success))
            } else {
                statusIcon.setImageResource(R.drawable.ic_error)
                statusIcon.setColorFilter(ContextCompat.getColor(context, R.color.status_error))
            }

            if (step.thinking.isNotBlank()) {
                thinkingSection.visibility = View.VISIBLE
                thinkingText.text = step.thinking
            } else {
                thinkingSection.visibility = View.GONE
            }

            if (step.screenshotPath != null || step.annotatedScreenshotPath != null) {
                screenshotSection.visibility = View.VISIBLE
                screenshotImage.setImageDrawable(null)

                val defaultPath = step.annotatedScreenshotPath ?: step.screenshotPath
                loadScreenshot(defaultPath, screenshotImage)

                val hasAnnotated = step.annotatedScreenshotPath != null
                btnAnnotated.visibility = if (hasAnnotated) View.VISIBLE else View.GONE

                btnOriginal.setOnClickListener {
                    loadScreenshot(step.screenshotPath, screenshotImage)
                    btnOriginal.alpha = 1f
                    btnAnnotated.alpha = 0.5f
                }

                btnAnnotated.setOnClickListener {
                    loadScreenshot(step.annotatedScreenshotPath, screenshotImage)
                    btnOriginal.alpha = 0.5f
                    btnAnnotated.alpha = 1f
                }

                if (hasAnnotated) {
                    btnOriginal.alpha = 0.5f
                    btnAnnotated.alpha = 1f
                } else {
                    btnOriginal.alpha = 1f
                }
            } else {
                screenshotSection.visibility = View.GONE
            }

            if (!step.message.isNullOrBlank()) {
                messageText.visibility = View.VISIBLE
                messageText.text = step.message
            } else {
                messageText.visibility = View.GONE
            }
        }

        fun clearImage() {
            screenshotImage.setImageDrawable(null)
        }

        private fun loadScreenshot(path: String?, imageView: ImageView) {
            if (path == null) return

            loadedBitmaps[path]?.let {
                if (!it.isRecycled) {
                    imageView.setImageBitmap(it)
                    return
                }
            }

            loadingJobs[adapterPosition]?.cancel()

            loadingJobs[adapterPosition] =
                coroutineScope.launch {
                    val bitmap =
                        withContext(Dispatchers.IO) {
                            historyManager.getScreenshotBitmap(path)
                        }
                    bitmap?.let {
                        loadedBitmaps[path] = it
                        if (currentStep?.screenshotPath == path || currentStep?.annotatedScreenshotPath == path) {
                            imageView.setImageBitmap(it)
                        }
                    }
                }
        }
    }

    companion object {
        private const val TAG = "HistoryDetailAdapter"
        private const val TYPE_HEADER = 0
        private const val TYPE_STEP = 1
        private const val TYPE_PLANNING_ROUND = 2
    }
}
