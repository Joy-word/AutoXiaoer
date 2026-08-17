package com.flowmate.autoxiaoer.config

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.util.Locale

class AppLanguageTest : StringSpec({
    "mainland simplified Chinese uses Chinese" {
        AppLanguage.fromSystemLocale(Locale.SIMPLIFIED_CHINESE) shouldBe AppLanguage.SIMPLIFIED_CHINESE
        AppLanguage.fromSystemLocale(Locale("zh", "CN")) shouldBe AppLanguage.SIMPLIFIED_CHINESE
    }

    "traditional and non-Chinese locales use English" {
        AppLanguage.fromSystemLocale(Locale.TRADITIONAL_CHINESE) shouldBe AppLanguage.ENGLISH
        AppLanguage.fromSystemLocale(Locale("zh", "TW")) shouldBe AppLanguage.ENGLISH
        AppLanguage.fromSystemLocale(Locale.ENGLISH) shouldBe AppLanguage.ENGLISH
    }

    "prompt language accepts legacy Chinese codes and defaults to English" {
        AppLanguage.fromPromptCode("cn") shouldBe AppLanguage.SIMPLIFIED_CHINESE
        AppLanguage.fromPromptCode("zh") shouldBe AppLanguage.SIMPLIFIED_CHINESE
        AppLanguage.fromPromptCode("en") shouldBe AppLanguage.ENGLISH
        AppLanguage.fromPromptCode("invalid") shouldBe AppLanguage.ENGLISH
    }
})