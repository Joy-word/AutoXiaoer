package com.flowmate.autoxiaoer.clawbot

/**
 * Persistent credentials returned after a successful ClawBot QR code login.
 *
 * @property botToken Bearer token used in Authorization header for all API calls.
 * @property baseUrl  Base URL for the iLink API (may differ from the default).
 * @property ilinkBotId  Bot's iLink user id.
 * @property ilinkUserId iLink user id of the authenticated WeChat account.
 */
data class ClawBotCredentials(
    val botToken: String,
    val baseUrl: String,
    val ilinkBotId: String,
    val ilinkUserId: String,
)
