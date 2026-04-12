package com.flowmate.autoxiaoer.voice

import com.flowmate.autoxiaoer.util.Logger
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.unmockkObject

/**
 * Property-based tests for [WakeWordDetector].
 *
 * Tests universal properties that should hold for all valid inputs.
 *
 * **Feature: unit-tests, Property 7: Exact match detection**
 * **Validates: Requirements 3.1, 3.5**
 */
class WakeWordDetectorExactMatchPropertyTest :
    StringSpec({

        beforeSpec {
            mockkObject(Logger)
            every { Logger.d(any(), any()) } just Runs
            every { Logger.w(any(), any()) } just Runs
            every { Logger.e(any(), any()) } just Runs
        }

        afterSpec {
            unmockkObject(Logger)
        }

        /**
         * Property 7: Exact match detection
         *
         * *For any* text containing a wake word (case-insensitive),
         * detect() SHALL return the matched wake word.
         *
         * **Validates: Requirements 3.1, 3.5**
         */
        "Property 7: Text containing wake word returns the wake word" {
            val wakeWords = listOf("hello", "assistant", "hey", "小智")
            val prefixes = listOf("", "please ", "ok ", "好的 ", "  ")
            val suffixes = listOf("", " help me", " 帮我", " please", "  ")

            checkAll(
                100,
                Arb.element(wakeWords),
                Arb.element(prefixes),
                Arb.element(suffixes),
            ) { wakeWord, prefix, suffix ->
                val detector = WakeWordDetector(listOf(wakeWord))
                val text = "$prefix$wakeWord$suffix"

                val result = detector.detect(text)

                result shouldBe wakeWord
            }
        }

        /**
         * Property 7b: Case-insensitive exact match detection
         *
         * *For any* wake word and text with different casing,
         * detect() SHALL still return the original wake word.
         *
         * **Validates: Requirements 3.5**
         */
        "Property 7b: Case-insensitive detection returns original wake word" {
            val wakeWords = listOf("Hello", "ASSISTANT", "Hey", "HeyAssistant")
            val caseTransforms =
                listOf<(String) -> String>(
                    { it.lowercase() },
                    { it.uppercase() },
                    { it }, // original case
                )

            checkAll(100, Arb.element(wakeWords), Arb.element(caseTransforms)) { wakeWord, transform ->
                val detector = WakeWordDetector(listOf(wakeWord))
                val transformedText = transform(wakeWord) + " help me"

                val result = detector.detect(transformedText)

                result shouldBe wakeWord
            }
        }

        /**
         * Property 7c: Multiple wake words - first match wins
         *
         * *For any* text containing multiple wake words,
         * detect() SHALL return the first wake word from the list that matches.
         *
         * **Validates: Requirements 3.1**
         */
        "Property 7c: Multiple wake words returns first match from list" {
            checkAll(100, Arb.int(0..2)) { firstIndex ->
                val wakeWords = listOf("hello", "hey", "hi")
                val detector = WakeWordDetector(wakeWords)
                // Text contains the wake word at firstIndex
                val text = "say ${wakeWords[firstIndex]} to everyone"

                val result = detector.detect(text)

                result shouldBe wakeWords[firstIndex]
            }
        }
    })

/**
 * Property-based tests for startsWithWakeWord functionality.
 *
 * **Feature: unit-tests, Property 9: startsWithWakeWord correctness**
 * **Validates: Requirements 3.7**
 */
class WakeWordDetectorStartsWithPropertyTest :
    StringSpec({

        beforeSpec {
            mockkObject(Logger)
            every { Logger.d(any(), any()) } just Runs
            every { Logger.w(any(), any()) } just Runs
            every { Logger.e(any(), any()) } just Runs
        }

        afterSpec {
            unmockkObject(Logger)
        }

        /**
         * Property 9: startsWithWakeWord correctness
         *
         * *For any* text starting with a wake word,
         * startsWithWakeWord() SHALL return the wake word and the correct remaining text.
         *
         * **Validates: Requirements 3.7**
         */
        "Property 9: startsWithWakeWord returns wake word and remaining text" {
            val wakeWords = listOf("hello", "hey", "小智", "assistant")
            val remainingTexts = listOf("", " help me", " 帮我打开微信", " open the app", " please do this")

            checkAll(100, Arb.element(wakeWords), Arb.element(remainingTexts)) { wakeWord, remaining ->
                val detector = WakeWordDetector(listOf(wakeWord))
                val text = wakeWord + remaining

                val result = detector.startsWithWakeWord(text)

                result shouldNotBe null
                result?.first shouldBe wakeWord
                result?.second shouldBe remaining.trim()
            }
        }

        /**
         * Property 9b: startsWithWakeWord with case variations
         *
         * *For any* text starting with a wake word in different case,
         * startsWithWakeWord() SHALL return the original wake word and remaining text.
         *
         * **Validates: Requirements 3.7**
         */
        "Property 9b: startsWithWakeWord handles case variations" {
            val wakeWords = listOf("Hello", "ASSISTANT", "Hey")
            val caseTransforms =
                listOf<(String) -> String>(
                    { it.lowercase() },
                    { it.uppercase() },
                )
            val remainingTexts = listOf(" help me", " please", " do this")

            checkAll(
                100,
                Arb.element(wakeWords),
                Arb.element(caseTransforms),
                Arb.element(remainingTexts),
            ) { wakeWord, transform, remaining ->
                val detector = WakeWordDetector(listOf(wakeWord))
                val text = transform(wakeWord) + remaining

                val result = detector.startsWithWakeWord(text)

                result shouldNotBe null
                result?.first shouldBe wakeWord
                result?.second shouldBe remaining.trim()
            }
        }

        /**
         * Property 9c: startsWithWakeWord returns null when wake word not at start
         *
         * *For any* text where wake word appears but not at the start,
         * startsWithWakeWord() SHALL return null.
         *
         * **Validates: Requirements 3.7**
         */
        "Property 9c: startsWithWakeWord returns null when wake word not at start" {
            val wakeWords = listOf("hello", "assistant", "小智")
            val prefixes = listOf("please ", "say ", "请 ", "ok ")

            checkAll(100, Arb.element(wakeWords), Arb.element(prefixes)) { wakeWord, prefix ->
                val detector = WakeWordDetector(listOf(wakeWord))
                val text = prefix + wakeWord + " help me"

                val result = detector.startsWithWakeWord(text)

                result shouldBe null
            }
        }

        /**
         * Property 9d: startsWithWakeWord with empty/blank text returns null
         *
         * *For any* empty or blank text,
         * startsWithWakeWord() SHALL return null.
         *
         * **Validates: Requirements 3.7**
         */
        "Property 9d: startsWithWakeWord with empty or blank text returns null" {
            val emptyTexts = listOf("", " ", "  ", "\t", "\n", "   \t\n  ")
            val wakeWords = listOf("hello", "小智")

            checkAll(100, Arb.element(wakeWords), Arb.element(emptyTexts)) { wakeWord, emptyText ->
                val detector = WakeWordDetector(listOf(wakeWord))

                val result = detector.startsWithWakeWord(emptyText)

                result shouldBe null
            }
        }
    })
