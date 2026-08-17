package com.flowmate.autoxiaoer.config

import android.os.Build
import java.util.Locale

enum class AppLanguage(val code: String) {
    SIMPLIFIED_CHINESE("cn"),
    ENGLISH("en");

    companion object {
        fun fromSystemLocale(locale: Locale): AppLanguage {
            if (locale.language != Locale.CHINESE.language) return ENGLISH

            val script = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) locale.script else ""
            return if (script.equals("Hans", ignoreCase = true) ||
                (script.isEmpty() && locale.country.equals("CN", ignoreCase = true))
            ) {
                SIMPLIFIED_CHINESE
            } else {
                ENGLISH
            }
        }

        fun fromPromptCode(value: String?): AppLanguage =
            if (value.equals("cn", ignoreCase = true) ||
                value.equals("zh", ignoreCase = true) ||
                value.equals("chinese", ignoreCase = true)
            ) {
                SIMPLIFIED_CHINESE
            } else {
                ENGLISH
            }
    }
}