package com.flowmate.autoxiaoer.voice

import com.flowmate.autoxiaoer.util.Logger
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [WakeWordDetector].
 *
 * Tests wake word detection including exact matching, case insensitivity,
 * fuzzy matching, and startsWithWakeWord functionality.
 *
 * _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7_
 */
class WakeWordDetectorTest {
    @BeforeEach
    fun setup() {
        mockkObject(Logger)
        every { Logger.d(any(), any()) } just Runs
        every { Logger.w(any(), any()) } just Runs
        every { Logger.e(any(), any()) } just Runs
    }

    @AfterEach
    fun teardown() {
        unmockkObject(Logger)
    }

    // ==================== Exact Match Tests ====================
    // _Requirements: 3.1_

    @Test
    fun `detect_textContainsExactWakeWord_returnsWakeWord`() {
        // Given
        val wakeWords = listOf("小智", "hey assistant")
        val detector = WakeWordDetector(wakeWords)
        val text = "小智帮我打开微信"

        // When
        val result = detector.detect(text)

        // Then
        assertEquals("小智", result)
    }

    @Test
    fun `detect_textContainsWakeWordInMiddle_returnsWakeWord`() {
        // Given
        val wakeWords = listOf("assistant")
        val detector = WakeWordDetector(wakeWords)
        val text = "hey assistant please help me"

        // When
        val result = detector.detect(text)

        // Then
        assertEquals("assistant", result)
    }

    @Test
    fun `detect_multipleWakeWords_returnsFirstMatch`() {
        // Given
        val wakeWords = listOf("hello", "hi", "hey")
        val detector = WakeWordDetector(wakeWords)
        val text = "hi there, how are you"

        // When
        val result = detector.detect(text)

        // Then
        assertEquals("hi", result)
    }

    // ==================== No Match Tests ====================
    // _Requirements: 3.2_

    @Test
    fun `detect_textDoesNotContainWakeWord_returnsNull`() {
        // Given
        val wakeWords = listOf("小智", "assistant")
        val detector = WakeWordDetector(wakeWords)
        val text = "今天天气怎么样"

        // When
        val result = detector.detect(text)

        // Then
        assertNull(result)
    }

    @Test
    fun `detect_partialWakeWordMatch_returnsNull`() {
        // Given
        val wakeWords = listOf("assistant")
        val detector = WakeWordDetector(wakeWords, sensitivity = 0.2f) // Low sensitivity
        val text = "assist me please"

        // When
        val result = detector.detect(text)

        // Then
        assertNull(result)
    }

    // ==================== Empty/Blank Text Tests ====================
    // _Requirements: 3.3_

    @Test
    fun `detect_emptyText_returnsNull`() {
        // Given
        val wakeWords = listOf("小智")
        val detector = WakeWordDetector(wakeWords)
        val text = ""

        // When
        val result = detector.detect(text)

        // Then
        assertNull(result)
    }

    @Test
    fun `detect_blankText_returnsNull`() {
        // Given
        val wakeWords = listOf("小智")
        val detector = WakeWordDetector(wakeWords)
        val text = "   "

        // When
        val result = detector.detect(text)

        // Then
        assertNull(result)
    }

    @Test
    fun `detect_whitespaceOnlyText_returnsNull`() {
        // Given
        val wakeWords = listOf("hello")
        val detector = WakeWordDetector(wakeWords)
        val text = "\t\n  "

        // When
        val result = detector.detect(text)

        // Then
        assertNull(result)
    }

    // ==================== Empty Wake Word List Tests ====================
    // _Requirements: 3.4_

    @Test
    fun `detect_emptyWakeWordList_returnsNull`() {
        // Given
        val wakeWords = emptyList<String>()
        val detector = WakeWordDetector(wakeWords)
        val text = "小智帮我打开微信"

        // When
        val result = detector.detect(text)

        // Then
        assertNull(result)
    }

    // ==================== Case Insensitivity Tests ====================
    // _Requirements: 3.5_

    @Test
    fun `detect_wakeWordDifferentCase_returnsWakeWord`() {
        // Given
        val wakeWords = listOf("Hello")
        val detector = WakeWordDetector(wakeWords)
        val text = "HELLO world"

        // When
        val result = detector.detect(text)

        // Then
        assertEquals("Hello", result)
    }

    @Test
    fun `detect_mixedCaseWakeWord_returnsWakeWord`() {
        // Given
        val wakeWords = listOf("HeyAssistant")
        val detector = WakeWordDetector(wakeWords)
        val text = "heyassistant please help"

        // When
        val result = detector.detect(text)

        // Then
        assertEquals("HeyAssistant", result)
    }

    @Test
    fun `detect_lowercaseTextUppercaseWakeWord_returnsWakeWord`() {
        // Given
        val wakeWords = listOf("ASSISTANT")
        val detector = WakeWordDetector(wakeWords)
        val text = "hey assistant help me"

        // When
        val result = detector.detect(text)

        // Then
        assertEquals("ASSISTANT", result)
    }

    // ==================== Fuzzy Match Tests (High Sensitivity) ====================
    // _Requirements: 3.6_

    @Test
    fun `detect_highSensitivityWithSimilarWord_returnsWakeWord`() {
        // Given - high sensitivity enables fuzzy matching
        // The fuzzy match uses Levenshtein distance with threshold = 0.5 + (sensitivity * 0.4)
        // For sensitivity 0.9, threshold = 0.86, so we need very close matches
        val wakeWords = listOf("hello")
        val detector = WakeWordDetector(wakeWords, sensitivity = 0.9f)
        val text = "helloo world" // One extra 'o' - similarity should be high enough

        // When
        val result = detector.detect(text)

        // Then
        assertEquals("hello", result)
    }

    @Test
    fun `detect_highSensitivityWithMinorTypo_returnsWakeWord`() {
        // Given - using a longer word where one character difference has less impact
        val wakeWords = listOf("assistant")
        val detector = WakeWordDetector(wakeWords, sensitivity = 0.5f)
        val text = "assistantt help me" // One extra 't' - high similarity

        // When
        val result = detector.detect(text)

        // Then
        assertEquals("assistant", result)
    }

    @Test
    fun `detect_lowSensitivityWithTypo_returnsNull`() {
        // Given - low sensitivity disables fuzzy matching
        val wakeWords = listOf("assistant")
        val detector = WakeWordDetector(wakeWords, sensitivity = 0.2f)
        val text = "assistent help me"

        // When
        val result = detector.detect(text)

        // Then
        assertNull(result)
    }

    @Test
    fun `detect_mediumSensitivityWithExactMatch_returnsWakeWord`() {
        // Given
        val wakeWords = listOf("小智")
        val detector = WakeWordDetector(wakeWords, sensitivity = 0.5f)
        val text = "小智你好"

        // When
        val result = detector.detect(text)

        // Then
        assertEquals("小智", result)
    }

    // ==================== startsWithWakeWord Tests ====================
    // _Requirements: 3.7_

    @Test
    fun `startsWithWakeWord_textStartsWithWakeWord_returnsPairWithRemainingText`() {
        // Given
        val wakeWords = listOf("小智")
        val detector = WakeWordDetector(wakeWords)
        val text = "小智帮我打开微信"

        // When
        val result = detector.startsWithWakeWord(text)

        // Then
        assertNotNull(result)
        assertEquals("小智", result?.first)
        assertEquals("帮我打开微信", result?.second)
    }

    @Test
    fun `startsWithWakeWord_textStartsWithWakeWordAndSpace_returnsPairWithTrimmedRemainingText`() {
        // Given
        val wakeWords = listOf("hey")
        val detector = WakeWordDetector(wakeWords)
        val text = "hey open the app"

        // When
        val result = detector.startsWithWakeWord(text)

        // Then
        assertNotNull(result)
        assertEquals("hey", result?.first)
        assertEquals("open the app", result?.second)
    }

    @Test
    fun `startsWithWakeWord_textDoesNotStartWithWakeWord_returnsNull`() {
        // Given
        val wakeWords = listOf("小智")
        val detector = WakeWordDetector(wakeWords)
        val text = "请小智帮我打开微信"

        // When
        val result = detector.startsWithWakeWord(text)

        // Then
        assertNull(result)
    }

    @Test
    fun `startsWithWakeWord_wakeWordInMiddle_returnsNull`() {
        // Given
        val wakeWords = listOf("assistant")
        val detector = WakeWordDetector(wakeWords)
        val text = "hey assistant help me"

        // When
        val result = detector.startsWithWakeWord(text)

        // Then
        assertNull(result)
    }

    @Test
    fun `startsWithWakeWord_emptyText_returnsNull`() {
        // Given
        val wakeWords = listOf("小智")
        val detector = WakeWordDetector(wakeWords)
        val text = ""

        // When
        val result = detector.startsWithWakeWord(text)

        // Then
        assertNull(result)
    }

    @Test
    fun `startsWithWakeWord_emptyWakeWordList_returnsNull`() {
        // Given
        val wakeWords = emptyList<String>()
        val detector = WakeWordDetector(wakeWords)
        val text = "小智帮我打开微信"

        // When
        val result = detector.startsWithWakeWord(text)

        // Then
        assertNull(result)
    }

    @Test
    fun `startsWithWakeWord_wakeWordIsEntireText_returnsEmptyRemainingText`() {
        // Given
        val wakeWords = listOf("hello")
        val detector = WakeWordDetector(wakeWords)
        val text = "hello"

        // When
        val result = detector.startsWithWakeWord(text)

        // Then
        assertNotNull(result)
        assertEquals("hello", result?.first)
        assertEquals("", result?.second)
    }

    @Test
    fun `startsWithWakeWord_caseInsensitive_returnsPairWithOriginalWakeWord`() {
        // Given
        val wakeWords = listOf("Hello")
        val detector = WakeWordDetector(wakeWords)
        val text = "HELLO world"

        // When
        val result = detector.startsWithWakeWord(text)

        // Then
        assertNotNull(result)
        assertEquals("Hello", result?.first)
        assertEquals("world", result?.second)
    }

    // ==================== Edge Cases ====================

    @Test
    fun `detect_wakeWordWithPunctuation_handlesNormalization`() {
        // Given - wake word with punctuation should be normalized
        val wakeWords = listOf("hey, assistant")
        val detector = WakeWordDetector(wakeWords)
        val text = "hey assistant help me"

        // When
        val result = detector.detect(text)

        // Then
        assertEquals("hey, assistant", result)
    }

    @Test
    fun `detect_chineseWakeWord_detectsCorrectly`() {
        // Given
        val wakeWords = listOf("你好小智")
        val detector = WakeWordDetector(wakeWords)
        val text = "你好小智请帮我查天气"

        // When
        val result = detector.detect(text)

        // Then
        assertEquals("你好小智", result)
    }

    @Test
    fun `detect_multipleWakeWordsFirstNotPresent_returnsSecondMatch`() {
        // Given
        val wakeWords = listOf("小智", "assistant", "hey")
        val detector = WakeWordDetector(wakeWords)
        val text = "hey there how are you"

        // When
        val result = detector.detect(text)

        // Then
        assertEquals("hey", result)
    }
}
