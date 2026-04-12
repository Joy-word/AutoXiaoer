package com.flowmate.autoxiaoer.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for [I18n] internationalization module.
 *
 * Tests message retrieval, formatting, and language utilities.
 *
 * _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 6.7_
 */
class I18nTest {
    // ==================== getMessage Tests ====================
    // _Requirements: 6.1, 6.2, 6.3_

    @Test
    fun `getMessage_validKeyWithChinese_returnsChineseMessage`() {
        // Given
        val key = "loading"
        val language = "cn"

        // When
        val result = I18n.getMessage(key, language)

        // Then
        assertEquals("加载中...", result)
    }

    @Test
    fun `getMessage_validKeyWithEnglish_returnsEnglishMessage`() {
        // Given
        val key = "loading"
        val language = "en"

        // When
        val result = I18n.getMessage(key, language)

        // Then
        assertEquals("Loading...", result)
    }

    @Test
    fun `getMessage_validKeyWithEnglishFull_returnsEnglishMessage`() {
        // Given
        val key = "error"
        val language = "english"

        // When
        val result = I18n.getMessage(key, language)

        // Then
        assertEquals("Error", result)
    }

    @Test
    fun `getMessage_invalidKey_returnsKeyAsFallback`() {
        // Given
        val key = "non_existent_key"
        val language = "cn"

        // When
        val result = I18n.getMessage(key, language)

        // Then
        assertEquals(key, result)
    }

    @Test
    fun `getMessage_invalidKeyWithEnglish_returnsKeyAsFallback`() {
        // Given
        val key = "another_invalid_key"
        val language = "en"

        // When
        val result = I18n.getMessage(key, language)

        // Then
        assertEquals(key, result)
    }

    @Test
    fun `getMessage_unknownLanguage_defaultsToChinese`() {
        // Given
        val key = "confirm"
        val language = "fr" // French - not supported

        // When
        val result = I18n.getMessage(key, language)

        // Then
        assertEquals("确认", result) // Chinese default
    }

    @Test
    fun `getMessage_caseInsensitiveLanguage_returnsCorrectMessage`() {
        // Given
        val key = "cancel"
        val languageUpper = "EN"
        val languageMixed = "English"

        // When
        val resultUpper = I18n.getMessage(key, languageUpper)
        val resultMixed = I18n.getMessage(key, languageMixed)

        // Then
        assertEquals("Cancel", resultUpper)
        assertEquals("Cancel", resultMixed)
    }

    // ==================== getFormattedMessage Tests ====================
    // _Requirements: 6.4, 6.5_

    @Test
    fun `getFormattedMessage_withValidArgs_returnsFormattedString`() {
        // Given - "step" message doesn't have format specifiers, so we test with a custom scenario
        // The step message is just "步骤" or "Step", so let's test the formatting mechanism
        val key = "step"
        val language = "cn"

        // When - getMessage returns "步骤", format with no args should work
        val result = I18n.getFormattedMessage(key, language)

        // Then
        assertEquals("步骤", result)
    }

    @Test
    fun `getFormattedMessage_withExtraArgs_returnsFormattedString`() {
        // Given - template without format specifiers, extra args should be ignored
        val key = "loading"
        val language = "en"

        // When
        val result = I18n.getFormattedMessage(key, language, "extra", "args")

        // Then - should return the template as-is since no format specifiers
        assertEquals("Loading...", result)
    }

    @Test
    fun `getFormattedMessage_withInvalidKey_returnsKeyAsFallback`() {
        // Given
        val key = "invalid_format_key"
        val language = "cn"

        // When
        val result = I18n.getFormattedMessage(key, language, 1, 2, 3)

        // Then
        assertEquals(key, result)
    }

    @Test
    fun `getFormattedMessage_englishMessage_returnsCorrectMessage`() {
        // Given
        val key = "task_completed"
        val language = "en"

        // When
        val result = I18n.getFormattedMessage(key, language)

        // Then
        assertEquals("Task Completed", result)
    }

    // ==================== getMessages Tests ====================
    // _Requirements: 6.6_

    @Test
    fun `getMessages_chinese_returnsCompleteMap`() {
        // Given
        val language = "cn"

        // When
        val result = I18n.getMessages(language)

        // Then
        assertNotNull(result)
        assertTrue(result.isNotEmpty())
        assertTrue(result.containsKey("loading"))
        assertTrue(result.containsKey("error"))
        assertTrue(result.containsKey("confirm"))
        assertEquals("加载中...", result["loading"])
    }

    @Test
    fun `getMessages_english_returnsCompleteMap`() {
        // Given
        val language = "en"

        // When
        val result = I18n.getMessages(language)

        // Then
        assertNotNull(result)
        assertTrue(result.isNotEmpty())
        assertTrue(result.containsKey("loading"))
        assertTrue(result.containsKey("error"))
        assertTrue(result.containsKey("confirm"))
        assertEquals("Loading...", result["loading"])
    }

    @Test
    fun `getMessages_unknownLanguage_returnsChineseMap`() {
        // Given
        val language = "de" // German - not supported

        // When
        val result = I18n.getMessages(language)

        // Then
        assertEquals("加载中...", result["loading"]) // Chinese default
    }

    // ==================== Languages.getDisplayName Tests ====================
    // _Requirements: 6.7_

    @Test
    fun `getDisplayName_chineseInChinese_returnsChineseDisplayName`() {
        // Given
        val code = I18n.Languages.CHINESE
        val inLanguage = I18n.Languages.CHINESE

        // When
        val result = I18n.Languages.getDisplayName(code, inLanguage)

        // Then
        assertEquals("中文", result)
    }

    @Test
    fun `getDisplayName_chineseInEnglish_returnsEnglishDisplayName`() {
        // Given
        val code = I18n.Languages.CHINESE
        val inLanguage = I18n.Languages.ENGLISH

        // When
        val result = I18n.Languages.getDisplayName(code, inLanguage)

        // Then
        assertEquals("Chinese", result)
    }

    @Test
    fun `getDisplayName_englishInChinese_returnsChineseDisplayName`() {
        // Given
        val code = I18n.Languages.ENGLISH
        val inLanguage = I18n.Languages.CHINESE

        // When
        val result = I18n.Languages.getDisplayName(code, inLanguage)

        // Then
        assertEquals("英文", result)
    }

    @Test
    fun `getDisplayName_englishInEnglish_returnsEnglishDisplayName`() {
        // Given
        val code = I18n.Languages.ENGLISH
        val inLanguage = I18n.Languages.ENGLISH

        // When
        val result = I18n.Languages.getDisplayName(code, inLanguage)

        // Then
        assertEquals("English", result)
    }

    @Test
    fun `getDisplayName_unknownCode_returnsCodeItself`() {
        // Given
        val code = "fr" // French - not supported

        // When
        val result = I18n.Languages.getDisplayName(code)

        // Then
        assertEquals("fr", result)
    }

    @Test
    fun `getDisplayName_defaultInLanguage_usesCodeAsInLanguage`() {
        // Given
        val code = I18n.Languages.CHINESE

        // When - inLanguage defaults to code
        val result = I18n.Languages.getDisplayName(code)

        // Then
        assertEquals("中文", result)
    }

    // ==================== Languages Constants Tests ====================

    @Test
    fun `languages_constants_haveCorrectValues`() {
        // Then
        assertEquals("cn", I18n.Languages.CHINESE)
        assertEquals("en", I18n.Languages.ENGLISH)
    }

    @Test
    fun `languages_all_containsAllLanguages`() {
        // Then
        assertEquals(2, I18n.Languages.ALL.size)
        assertTrue(I18n.Languages.ALL.contains(I18n.Languages.CHINESE))
        assertTrue(I18n.Languages.ALL.contains(I18n.Languages.ENGLISH))
    }
}
