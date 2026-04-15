package com.flowmate.autoxiaoer.clawbot

import android.util.Base64
import com.flowmate.autoxiaoer.util.Logger
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Response from GET /get_bot_qrcode.
 *
 * @property qrcode       Opaque QR code id used for polling status (e.g. "qrc_xxx").
 * @property qrcodeImgContent  URL that encodes the WeChat login QR code content.
 */
data class ClawBotQrCodeResponse(
    val qrcode: String,
    val qrcodeImgContent: String,
)

/**
 * Status returned by GET /get_qrcode_status.
 *
 * Possible values: "wait", "scaned", "confirmed", "expired".
 */
data class ClawBotQrCodeStatusResponse(
    val status: String,
    /** Only present when status == "confirmed". */
    val botToken: String? = null,
    val ilinkBotId: String? = null,
    val ilinkUserId: String? = null,
    val baseUrl: String? = null,
)

/**
 * A single incoming message from /getupdates.
 *
 * NOTE: The iLink protocol documentation does not publish the exact field names inside
 * the `msgs` array. The field names below (`from_user_id`, `content`) are inferred from
 * the /sendmessage API and community reports.  See [KNOWN_ISSUES] in getupdates for full
 * details — these names may need adjustment after live testing.
 */
data class ClawBotIncomingMessage(
    val fromUserId: String,
    val contextToken: String,
    /** Plain-text body. May be null for non-text message types. */
    val text: String?,
)

/**
 * Response from POST /getupdates.
 *
 * @property ret         0 = success; -14 = session expired.
 * @property msgs        Incoming messages (may be empty on timeout with no new messages).
 * @property contextToken Last context_token in this batch (convenience; prefer per-message token).
 * @property nextBuf     Opaque cursor to pass in the next /getupdates call.
 */
data class ClawBotUpdatesResponse(
    val ret: Int,
    val msgs: List<ClawBotIncomingMessage>,
    val contextToken: String,
    val nextBuf: String,
)

/**
 * Low-level HTTP client for the WeChat iLink Bot API.
 *
 * All network calls are blocking (call from a background coroutine / IO dispatcher).
 *
 * Default base URL: https://ilinkai.weixin.qq.com
 */
object ClawBotClient {
    private const val TAG = "ClawBotClient"

    const val DEFAULT_BASE_URL = "https://ilinkai.weixin.qq.com/ilink/bot"
    private const val CHANNEL_VERSION = "2.0.0"

    // iLink message protocol constants
    private const val MESSAGE_TYPE_BOT = 2
    private const val MESSAGE_STATE_FINISH = 2
    private const val MESSAGE_ITEM_TYPE_TEXT = 1
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    /**
     * Dedicated OkHttpClient.
     * read timeout MUST exceed the server's 35-second long-poll hold; 40 s gives comfortable margin.
     */
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // X-WECHAT-UIN helper
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Generates a fresh X-WECHAT-UIN value per the protocol spec:
     * random uint32 → decimal string → Base64.
     */
    private fun generateWeChatUin(): String {
        val uint32 = Random.nextInt().toLong() and 0xFFFFFFFFL
        return Base64.encodeToString(uint32.toString().toByteArray(), Base64.NO_WRAP)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Request builders
    // ──────────────────────────────────────────────────────────────────────────

    private fun buildGetRequest(url: String): Request =
        Request.Builder()
            .url(url)
            .get()
            .build()

    private fun buildAuthenticatedPostRequest(
        url: String,
        botToken: String,
        body: JSONObject,
    ): Request {
        // Merge base_info into every POST body
        body.put("base_info", JSONObject().put("channel_version", CHANNEL_VERSION))
        return Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", "application/json")
            .header("AuthorizationType", "ilink_bot_token")
            .header("Authorization", "Bearer $botToken")
            .header("X-WECHAT-UIN", generateWeChatUin())
            .header("iLink-App-ClientVersion", "1")
            .build()
    }

    /** Generates a 32-character random hex string for use as client_id. */
    private fun generateClientId(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Step 1: Obtain a login QR code.
     *
     * GET /get_bot_qrcode?bot_type=3
     * No auth required.
     *
     * @return [ClawBotQrCodeResponse] on success, null on network/parse error.
     */
    fun getBotQrCode(): ClawBotQrCodeResponse? {
        return try {
            val url = "$DEFAULT_BASE_URL/get_bot_qrcode?bot_type=3"
            val response = httpClient.newCall(buildGetRequest(url)).execute()
            val body = response.body?.string()
            if (body == null) {
                Logger.e(TAG, "getBotQrCode empty body, HTTP ${response.code}")
                return null
            }
            Logger.d(TAG, "getBotQrCode HTTP ${response.code}, body: ${body.take(300)}")
            if (!response.isSuccessful) {
                Logger.e(TAG, "getBotQrCode non-2xx: ${response.code} body=$body")
                return null
            }
            val json = JSONObject(body)
            ClawBotQrCodeResponse(
                qrcode = json.getString("qrcode"),
                qrcodeImgContent = json.getString("qrcode_img_content"),
            )
        } catch (e: Exception) {
            Logger.e(TAG, "getBotQrCode failed: ${e.javaClass.simpleName}: ${e.message}", e)
            null
        }
    }

    /**
     * Step 2: Poll whether the user has scanned / confirmed the QR code.
     *
     * GET /get_qrcode_status?qrcode=<id>
     * No auth required.
     *
     * @param qrcode The opaque qrcode id from [getBotQrCode].
     * @return [ClawBotQrCodeStatusResponse] on success, null on error.
     */
    fun getQrCodeStatus(qrcode: String): ClawBotQrCodeStatusResponse? {
        return try {
        val url = "$DEFAULT_BASE_URL/get_qrcode_status?qrcode=$qrcode"
        val response = httpClient.newCall(buildGetRequest(url)).execute()
        val body = response.body?.string() ?: return null
        Logger.d(TAG, "getQrCodeStatus response: ${body.take(200)}")
        val json = JSONObject(body)
        val status = json.optString("status", "wait")
        if (status == "confirmed") {
            ClawBotQrCodeStatusResponse(
                status = status,
                botToken = json.optString("bot_token").takeIf { it.isNotBlank() },
                ilinkBotId = json.optString("ilink_bot_id").takeIf { it.isNotBlank() },
                ilinkUserId = json.optString("ilink_user_id").takeIf { it.isNotBlank() },
                baseUrl = json.optString("baseurl").takeIf { it.isNotBlank() },
            )
        } else {
            ClawBotQrCodeStatusResponse(status = status)
        }
        } catch (e: Exception) {
            Logger.e(TAG, "getQrCodeStatus failed", e)
            null
        }
    }

    /**
     * Step 3 (loop): Long-poll for incoming messages.
     *
     * POST /getupdates
     * Server holds the connection for up to ~35 seconds before responding.
     *
     * Pass [getUpdatesBuf] = "" on the first call; use [ClawBotUpdatesResponse.nextBuf] on
     * subsequent calls to resume from where you left off.
     *
     * ──────────────────────────────────────────────────────────
     * KNOWN ISSUES WITH THIS IMPLEMENTATION ("getupdates 潜在问题"):
     *
     * 1. **未知 msgs 字段名** — iLink 协议文档未公开 `msgs` 数组内部的 JSON 字段名。
     *    本实现依赖以下推断：
     *      - 发送方用户 id → `from_user_id`（参考 /sendmessage 的 `to_user_id`）
     *      - 文本内容 → `content.str`（参考微信协议惯例），回退到 `text` / `content`
     *      - context_token → `context_token`（协议明确要求回传，字段名合理）
     *    实际字段名可能不同，需用真实 bot_token 抓包后修正 [parseIncomingMessage]。
     *
     * 2. **非文本消息** — 图片/文件/语音等消息类型的结构完全未知。
     *    当前实现若 text 字段为 null/空，触发任务时 taskDescription 为「（收到消息）」。
     *    建议后续根据实际消息结构扩展 [ClawBotIncomingMessage] 以支持 media_type。
     *
     * 3. **context_token 层级** — 文档描述 context_token 在响应顶层，但实际也可能
     *    在每条 msg 内各自携带。若顶层无 context_token，当前代码会尝试 msgs[0].contextToken。
     *    若该字段也不存在则为空字符串，导致回复无法路由——发现此情况应检查实际响应结构。
     *
     * 4. **get_updates_buf 格式** — 文档称其为"不透明游标"，但未说明初始值是否严格要求
     *    空字符串（vs null）。当前按协议传 ""，服务端若拒绝需改为 null / 省略该字段。
     *
     * 5. **并发消息** — 一次 getupdates 响应可能返回多条消息，当前实现会为每条消息
     *    各自触发一个任务。若 TaskExecutionManager 已在运行任务，后续消息会被丢弃
     *    （与 NotificationTrigger 行为一致）。如需队列化处理，需额外实现消息队列。
     * ──────────────────────────────────────────────────────────
     *
     * @param creds       Current login credentials.
     * @param getUpdatesBuf Opaque cursor ("" for first call).
     * @return [ClawBotUpdatesResponse] on success, null on hard network error.
     */
    fun getUpdates(creds: ClawBotCredentials, getUpdatesBuf: String): ClawBotUpdatesResponse? {
        return try {
            val url = "${creds.baseUrl}/getupdates"
            val requestBody = JSONObject().apply {
                put("get_updates_buf", getUpdatesBuf)
            }
            Logger.d(TAG, "getUpdates req url=$url buf=${getUpdatesBuf.take(40)}")
            val request = buildAuthenticatedPostRequest(url, creds.botToken, requestBody)
            val response = httpClient.newCall(request).execute()
            val bodyText = response.body?.string() ?: return null
            if (bodyText.isBlank()) {
                Logger.w(TAG, "getUpdates: empty body (HTTP ${response.code})")
                return null
            }
            Logger.d(TAG, "getUpdates HTTP=${response.code} response: ${bodyText.take(500)}")
            parseUpdatesResponse(bodyText)
        } catch (e: Exception) {
            Logger.e(TAG, "getUpdates failed", e)
            null
        }
    }

    /**
     * Sends a text message to a WeChat user.
     *
     * POST /sendmessage
     * The [contextToken] MUST be echoed back from the most recent incoming message for that user.
     *
     * @return true on success (ret == 0).
     */
    fun sendMessage(
        creds: ClawBotCredentials,
        toUserId: String,
        contextToken: String,
        text: String,
    ): Boolean {
        return try {
            val url = "${creds.baseUrl}/sendmessage"
            val textItem = JSONObject().put("text", text)
            val item = JSONObject().apply {
                put("type", MESSAGE_ITEM_TYPE_TEXT)
                put("text_item", textItem)
            }
            val msgBody = JSONObject().apply {
                put("to_user_id", toUserId)
                put("client_id", generateClientId())
                put("message_type", MESSAGE_TYPE_BOT)
                put("message_state", MESSAGE_STATE_FINISH)
                put("item_list", JSONArray().put(item))
                put("context_token", contextToken)
            }
            val requestBody = JSONObject().apply {
                put("msg", msgBody)
            }
            Logger.d(TAG, "sendMessage req url=$url toUser=$toUserId requestBody=${requestBody.toString().take(300)}")
            val request = buildAuthenticatedPostRequest(url, creds.botToken, requestBody)
            val response = httpClient.newCall(request).execute()
            val bodyText = response.body?.string() ?: return false
            Logger.i(TAG, "sendMessage HTTP=${response.code} response: ${bodyText.take(300)}")
            val json = JSONObject(bodyText)
            // Server returns {} (no "ret" field) on success — treat absent "ret" as 0.
            val ret = if (json.has("ret")) json.optInt("ret", -1) else 0
            Logger.i(TAG, "sendMessage ret=$ret")
            ret == 0
        } catch (e: Exception) {
            Logger.e(TAG, "sendMessage failed", e)
            false
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Parsing helpers
    // ──────────────────────────────────────────────────────────────────────────

    private fun parseUpdatesResponse(bodyText: String): ClawBotUpdatesResponse {
        val json = JSONObject(bodyText)
        val ret = json.optInt("ret", 0)
        // Server may signal session expiry via errcode instead of (or in addition to) ret.
        // Normalize: if either field is -14, expose ret=-14 so the polling loop handles it uniformly.
        val errCode = json.optInt("errcode", 0).takeIf { it != 0 }
            ?: json.optInt("err_code", 0)
        val effectiveRet = if (ret == -14 || errCode == -14) -14 else ret
        val topContextToken = json.optString("context_token", "")
        val nextBuf = json.optString("get_updates_buf", "")

        val msgs = mutableListOf<ClawBotIncomingMessage>()
        val msgsArray = json.optJSONArray("msgs")
        if (msgsArray != null) {
            for (i in 0 until msgsArray.length()) {
                parseIncomingMessage(msgsArray.getJSONObject(i))?.let { msgs.add(it) }
            }
        }

        // Determine the top-level context_token (fallback: first message's token)
        val resolvedContextToken = topContextToken.takeIf { it.isNotBlank() }
            ?: msgs.firstOrNull()?.contextToken ?: ""

        // Back-fill resolvedContextToken into any per-message entry that arrived without its own
        // token (server sometimes only sends context_token at the response top level).
        val filledMsgs = if (resolvedContextToken.isNotBlank()) {
            msgs.map { if (it.contextToken.isBlank()) it.copy(contextToken = resolvedContextToken) else it }
        } else msgs

        return ClawBotUpdatesResponse(
            ret = effectiveRet,
            msgs = filledMsgs,
            contextToken = resolvedContextToken,
            nextBuf = nextBuf,
        )
    }

    /**
     * Attempts to parse a single message object from the `msgs` array.
     *
     * Field names are inferred — see KNOWN ISSUES note on [getUpdates].
     */
    private fun parseIncomingMessage(obj: JSONObject): ClawBotIncomingMessage? {
        val fromUserId = obj.optString("from_user_id").takeIf { it.isNotBlank() } ?: return null
        val contextToken = obj.optString("context_token", "")

        // Try multiple candidate field paths for text content.
        // Primary path mirrors the sendmessage structure: item_list[0].text_item.text
        val text: String? = run {
            val itemList = obj.optJSONArray("item_list")
            if (itemList != null && itemList.length() > 0) {
                itemList.getJSONObject(0).optJSONObject("text_item")?.optString("text")?.takeIf { it.isNotBlank() }
            } else null
        }
            ?: obj.optString("text").takeIf { it.isNotBlank() }
            ?: obj.optJSONObject("content")?.optString("str")?.takeIf { it.isNotBlank() }
            ?: obj.optString("content").takeIf { it.isNotBlank() }

        return ClawBotIncomingMessage(
            fromUserId = fromUserId,
            contextToken = contextToken,
            text = text,
        )
    }
}
