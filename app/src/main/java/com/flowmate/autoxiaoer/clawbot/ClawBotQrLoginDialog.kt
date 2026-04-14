package com.flowmate.autoxiaoer.clawbot

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.flowmate.autoxiaoer.settings.SettingsManager
import com.flowmate.autoxiaoer.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * DialogFragment that guides the user through ClawBot QR-code login.
 *
 * Flow:
 *  1. Call getBotQrCode → encode `qrcode_img_content` to QR bitmap → display.
 *  2. Poll getQrCodeStatus every 5 s.
 *     - "confirmed" → save credentials → start service → invoke callback → dismiss.
 *     - "expired" inside 60 s window → refresh QR automatically.
 *  3. After 60 s total: hide QR, show "已过期，请刷新" + refresh button.
 *     Pressing refresh restarts the whole flow.
 */
class ClawBotQrLoginDialog : DialogFragment() {

    interface Callback {
        fun onConnected()
    }

    private var callback: Callback? = null
    private var loginJob: Job? = null

    // Views (built programmatically)
    private lateinit var qrImageView: ImageView
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnRefresh: Button

    fun setCallback(cb: Callback) {
        callback = cb
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        val dp = ctx.resources.displayMetrics.density

        val root = ScrollView(ctx)
        val container2 = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val pad = (24 * dp).toInt()
            setPadding(pad, pad, pad, pad)
        }
        root.addView(container2)

        // Title
        val title = TextView(ctx).apply {
            text = "扫码连接 ClawBot"
            textSize = 18f
            setTextColor(Color.parseColor("#1C1B1F"))
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            lp.bottomMargin = (8 * dp).toInt()
            layoutParams = lp
        }
        container2.addView(title)

        // Hint
        val hint = TextView(ctx).apply {
            text = "请使用微信扫描下方二维码完成授权"
            textSize = 14f
            setTextColor(Color.parseColor("#49454F"))
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            lp.bottomMargin = (16 * dp).toInt()
            layoutParams = lp
        }
        container2.addView(hint)

        // ProgressBar (shown while loading)
        progressBar = ProgressBar(ctx).apply {
            val size = (48 * dp).toInt()
            val lp = LinearLayout.LayoutParams(size, size)
            lp.gravity = Gravity.CENTER_HORIZONTAL
            lp.bottomMargin = (16 * dp).toInt()
            layoutParams = lp
            visibility = View.VISIBLE
        }
        container2.addView(progressBar)

        // QR ImageView
        qrImageView = ImageView(ctx).apply {
            val size = (240 * dp).toInt()
            val lp = LinearLayout.LayoutParams(size, size)
            lp.gravity = Gravity.CENTER_HORIZONTAL
            lp.bottomMargin = (16 * dp).toInt()
            layoutParams = lp
            visibility = View.GONE
        }
        container2.addView(qrImageView)

        // Status text
        statusText = TextView(ctx).apply {
            text = "正在获取二维码…"
            textSize = 13f
            setTextColor(Color.parseColor("#49454F"))
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            lp.bottomMargin = (16 * dp).toInt()
            layoutParams = lp
        }
        container2.addView(statusText)

        // Refresh button (hidden until timeout)
        btnRefresh = Button(ctx).apply {
            text = "刷新二维码"
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            lp.gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = lp
            visibility = View.GONE
            setOnClickListener { startLogin() }
        }
        container2.addView(btnRefresh)

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        startLogin()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        loginJob?.cancel()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Login flow
    // ──────────────────────────────────────────────────────────────────────────

    private fun startLogin() {
        loginJob?.cancel()
        btnRefresh.visibility = View.GONE
        qrImageView.visibility = View.GONE
        progressBar.visibility = View.VISIBLE
        statusText.text = "正在获取二维码…"

        loginJob = viewLifecycleOwner.lifecycleScope.launch {
            val qrResponse = withContext(Dispatchers.IO) { ClawBotClient.getBotQrCode() }

            if (qrResponse == null) {
                progressBar.visibility = View.GONE
                statusText.text = "获取二维码失败，请检查网络后重试。"
                btnRefresh.visibility = View.VISIBLE
                return@launch
            }

            // Generate QR bitmap on IO thread
            val bitmap = withContext(Dispatchers.IO) {
                encodeQrContent(qrResponse.qrcodeImgContent)
            }

            if (bitmap == null) {
                progressBar.visibility = View.GONE
                statusText.text = "生成二维码失败，请重试。"
                btnRefresh.visibility = View.VISIBLE
                return@launch
            }

            progressBar.visibility = View.GONE
            qrImageView.setImageBitmap(bitmap)
            qrImageView.visibility = View.VISIBLE
            statusText.text = "等待扫码…"

            // Poll loop (max 60 s)
            val startMs = System.currentTimeMillis()
            while (isActive && System.currentTimeMillis() - startMs < TIMEOUT_MS) {
                delay(POLL_INTERVAL_MS)
                if (!isActive) break

                val statusResp = withContext(Dispatchers.IO) {
                    ClawBotClient.getQrCodeStatus(qrResponse.qrcode)
                } ?: continue

                when (statusResp.status) {
                    "scaned" -> statusText.text = "已扫码，请在手机上确认…"
                    "confirmed" -> {
                        val ctx = requireContext()
                        val creds = ClawBotCredentials(
                            botToken = statusResp.botToken ?: "",
                            baseUrl = statusResp.baseUrl ?: ClawBotClient.DEFAULT_BASE_URL,
                            ilinkBotId = statusResp.ilinkBotId ?: "",
                            ilinkUserId = statusResp.ilinkUserId ?: "",
                        )
                        withContext(Dispatchers.IO) {
                            SettingsManager.getInstance(ctx).saveClawBotCredentials(creds)
                            ClawBotPollingService.start(ctx)
                        }
                        Logger.i(TAG, "ClawBot login confirmed, polling started")
                        callback?.onConnected()
                        dismissAllowingStateLoss()
                        return@launch
                    }
                    "expired" -> {
                        // Server says QR expired; refresh automatically
                        statusText.text = "二维码已失效，正在刷新…"
                        startLogin()
                        return@launch
                    }
                    else -> { /* "wait" — keep polling */ }
                }
            }

            // Timed out
            if (isActive) {
                qrImageView.visibility = View.GONE
                statusText.text = "二维码已过期，请点击刷新。"
                btnRefresh.visibility = View.VISIBLE
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // QR encoding
    // ──────────────────────────────────────────────────────────────────────────

    private fun encodeQrContent(content: String): Bitmap? = try {
        val size = 600
        val bitMatrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
        val pixels = IntArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                pixels[y * size + x] = if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also {
            it.setPixels(pixels, 0, size, 0, 0, size, size)
        }
    } catch (e: Exception) {
        Logger.e(TAG, "QR encoding failed", e)
        null
    }

    companion object {
        private const val TAG = "ClawBotQrLoginDialog"
        private const val POLL_INTERVAL_MS = 5_000L
        private const val TIMEOUT_MS = 60_000L
    }
}
