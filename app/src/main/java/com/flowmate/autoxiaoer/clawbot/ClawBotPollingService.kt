package com.flowmate.autoxiaoer.clawbot

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.flowmate.autoxiaoer.settings.SettingsManager
import com.flowmate.autoxiaoer.task.TaskExecutionManager
import com.flowmate.autoxiaoer.task.TriggerContext
import com.flowmate.autoxiaoer.task.TriggerType
import com.flowmate.autoxiaoer.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Background service that maintains the ClawBot long-poll message loop.
 *
 * Lifecycle:
 *  - Started via [start] when credentials are available (login or app restart).
 *  - Stopped via [stop] or when the server returns errcode -14 (session expired).
 *
 * On session expiry the service broadcasts [ACTION_SESSION_EXPIRED] so the UI can
 * update accordingly and prompt the user to re-scan a QR code.
 */
class ClawBotPollingService : Service() {
    companion object {
        const val TAG = "ClawBotPollingService"

        /** Broadcast action emitted when the iLink session expires (errcode -14). */
        const val ACTION_SESSION_EXPIRED = "com.flowmate.autoxiaoer.CLAWBOT_SESSION_EXPIRED"

        private val running = AtomicBoolean(false)

        /** @return true if the polling loop is currently active. */
        fun isRunning(): Boolean = running.get()

        fun start(context: Context) {
            Logger.i(TAG, "Starting ClawBotPollingService")
            context.startService(Intent(context, ClawBotPollingService::class.java))
        }

        fun stop(context: Context) {
            Logger.i(TAG, "Stopping ClawBotPollingService")
            context.stopService(Intent(context, ClawBotPollingService::class.java))
        }
    }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.IO)
    private var pollingJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (running.compareAndSet(false, true)) {
            Logger.i(TAG, "Polling service started, launching loop")
            pollingJob = serviceScope.launch { runPollingLoop() }
        } else {
            Logger.d(TAG, "Polling service already running, ignoring start command")
        }
        return START_STICKY
    }

    override fun onDestroy() {
        Logger.i(TAG, "Polling service destroyed")
        running.set(false)
        serviceJob.cancel()
        super.onDestroy()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Polling loop
    // ──────────────────────────────────────────────────────────────────────────

    private suspend fun runPollingLoop() {
        var getUpdatesBuf = ""

        while (currentCoroutineContext().isActive && running.get()) {
            val creds = SettingsManager.getInstance(applicationContext).getClawBotCredentials()
            if (creds == null) {
                Logger.w(TAG, "No credentials, stopping polling service")
                stopSelf()
                break
            }

            val response = try {
                ClawBotClient.getUpdates(creds, getUpdatesBuf)
            } catch (e: Exception) {
                Logger.e(TAG, "getUpdates exception, will retry after delay", e)
                delay(5_000)
                continue
            }

            if (response == null) {
                // Network error — back off and retry
                delay(5_000)
                continue
            }

            if (response.ret == -14) {
                Logger.w(TAG, "Session expired (ret=-14), clearing credentials and stopping")
                SettingsManager.getInstance(applicationContext).clearClawBotCredentials()
                sendBroadcast(Intent(ACTION_SESSION_EXPIRED))
                running.set(false)
                stopSelf()
                break
            }

            // Advance the cursor for the next request
            if (response.nextBuf.isNotEmpty()) {
                getUpdatesBuf = response.nextBuf
            }

            // Dispatch each message as a task (skip if another task is already running)
            for (msg in response.msgs) {
                handleIncomingMessage(msg)
            }
        }

        Logger.i(TAG, "Polling loop exited")
    }

    private fun handleIncomingMessage(msg: ClawBotIncomingMessage) {
        val text = msg.text?.trim()
        if (text.isNullOrBlank()) {
            Logger.d(TAG, "Received non-text ClawBot message, skipping (fromUserId=${msg.fromUserId})")
            return
        }

        if (TaskExecutionManager.isTaskRunning()) {
            Logger.w(TAG, "Task already running, dropping ClawBot message: ${text.take(50)}")
            return
        }

        val blockReason = TaskExecutionManager.getStartTaskBlockReason()
        if (blockReason != TaskExecutionManager.StartTaskBlockReason.NONE) {
            Logger.w(TAG, "Cannot start task (blockReason=$blockReason), dropping ClawBot message")
            return
        }

        val triggerContext = TriggerContext(
            triggerType = TriggerType.CLAWBOT,
            clawBotContextToken = msg.contextToken,
            clawBotFromUserId = msg.fromUserId,
        )

        // Persist the latest conversation so proactive sends (app-initiated tasks) can
        // push notifications back to this user even without an active triggerContext.
        if (msg.contextToken.isNotBlank()) {
            SettingsManager.getInstance(applicationContext)
                .saveClawBotLastConversation(msg.fromUserId, msg.contextToken)
        }

        Logger.i(TAG, "Triggering task from ClawBot message: ${text.take(80)}")
        TaskExecutionManager.startTask(text, triggerContext)
    }
}
