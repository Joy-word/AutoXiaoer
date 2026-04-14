package com.flowmate.autoxiaoer.clawbot

import android.content.Context
import com.flowmate.autoxiaoer.settings.SettingsManager
import com.flowmate.autoxiaoer.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Façade for all ClawBot-related operations.
 *
 * Provides a simple API for the rest of the app (UI, LLMAgent) without exposing
 * the details of [ClawBotClient] or [ClawBotPollingService].
 */
object ClawBotManager {
    private const val TAG = "ClawBotManager"

    // ──────────────────────────────────────────────────────────────────────────
    // Connection state
    // ──────────────────────────────────────────────────────────────────────────

    /** @return true if bot_token credentials are stored (does NOT verify the token is still valid). */
    fun isConnected(context: Context): Boolean =
        SettingsManager.getInstance(context).getClawBotCredentials() != null

    /** @return Stored credentials, or null if not connected. */
    fun getCredentials(context: Context): ClawBotCredentials? =
        SettingsManager.getInstance(context).getClawBotCredentials()

    // ──────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Starts the long-poll service if valid credentials are stored.
     * Safe to call at app startup — no-op if already running or not connected.
     */
    fun startPollingIfConnected(context: Context) {
        if (!isConnected(context)) {
            Logger.d(TAG, "Not connected, skipping poll start")
            return
        }
        if (ClawBotPollingService.isRunning()) {
            Logger.d(TAG, "Polling already running")
            return
        }
        Logger.i(TAG, "Starting ClawBot polling (credentials exist)")
        ClawBotPollingService.start(context)
    }

    /**
     * Clears stored credentials and stops the polling service.
     */
    fun disconnect(context: Context) {
        Logger.i(TAG, "Disconnecting ClawBot")
        SettingsManager.getInstance(context).clearClawBotCredentials()
        ClawBotPollingService.stop(context)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Messaging
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Sends a text message via ClawBot.
     * Must be called from a coroutine (suspends on IO dispatcher).
     *
     * @param toUserId      Recipient's WeChat iLink user id.
     * @param contextToken  context_token echoed from the most recent incoming message for this user.
     * @param text          Message body to send.
     * @return true if the server acknowledged the message (ret == 0).
     */
    suspend fun sendMessage(
        context: Context,
        toUserId: String,
        contextToken: String,
        text: String,
    ): Boolean = withContext(Dispatchers.IO) {
        val creds = getCredentials(context)
        if (creds == null) {
            Logger.w(TAG, "sendMessage called but not connected")
            return@withContext false
        }
        if (toUserId.isBlank() || contextToken.isBlank()) {
            Logger.w(TAG, "sendMessage called with blank toUserId or contextToken")
            return@withContext false
        }
        val ok = ClawBotClient.sendMessage(creds, toUserId, contextToken, text)
        Logger.i(TAG, "sendMessage toUserId=$toUserId ok=$ok")
        ok
    }
}
