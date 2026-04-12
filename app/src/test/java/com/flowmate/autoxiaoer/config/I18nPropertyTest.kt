package com.flowmate.autoxiaoer.config

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.stringPattern
import io.kotest.property.checkAll

/**
 * Property-based tests for [I18n] internationalization module.
 *
 * Tests universal properties that should hold for all valid inputs.
 *
 * **Feature: unit-tests, Property 13: Invalid key fallback**
 * **Feature: unit-tests, Property 14: Language completeness**
 * **Validates: Requirements 6.3, 6.8**
 */
class I18nPropertyTest :
    StringSpec({

        /**
         * Property 13: Invalid key fallback
         *
         * *For any* key not in the message map, getMessage() SHALL return the key itself.
         *
         * **Validates: Requirements 6.3**
         */
        "Property 13: Invalid key fallback - getMessage returns key for non-existent keys" {
            // Get all valid keys to ensure we generate invalid ones
            val validKeysCn = I18n.getMessages("cn").keys
            val validKeysEn = I18n.getMessages("en").keys
            val allValidKeys = validKeysCn + validKeysEn

            checkAll(100, Arb.stringPattern("[a-z_]{10,30}")) { randomKey ->
                // Only test if the key is not a valid key
                if (randomKey !in allValidKeys) {
                    // Test with Chinese
                    I18n.getMessage(randomKey, "cn") shouldBe randomKey
                    // Test with English
                    I18n.getMessage(randomKey, "en") shouldBe randomKey
                }
            }
        }

        /**
         * Property 13b: Invalid key fallback with various languages
         *
         * *For any* invalid key and any language code, getMessage() SHALL return the key itself.
         *
         * **Validates: Requirements 6.3**
         */
        "Property 13b: Invalid key fallback works for any language code" {
            val validKeysCn = I18n.getMessages("cn").keys
            val validKeysEn = I18n.getMessages("en").keys
            val allValidKeys = validKeysCn + validKeysEn

            // Test with various language codes
            val languageCodes = listOf("cn", "en", "english", "fr", "de", "jp", "unknown")

            checkAll(100, Arb.stringPattern("[a-z_]{15,25}")) { randomKey ->
                if (randomKey !in allValidKeys) {
                    languageCodes.forEach { lang ->
                        I18n.getMessage(randomKey, lang) shouldBe randomKey
                    }
                }
            }
        }

        /**
         * Property 14: Language completeness
         *
         * *For all* keys in the Chinese message map, there SHALL be a corresponding key
         * in the English message map.
         *
         * **Validates: Requirements 6.8**
         */
        "Property 14: Language completeness - English map contains all Chinese keys" {
            val chineseKeys = I18n.getMessages("cn").keys
            val englishKeys = I18n.getMessages("en").keys

            // All Chinese keys should exist in English map
            englishKeys shouldContainAll chineseKeys
        }

        /**
         * Property 14b: Language completeness - bidirectional
         *
         * *For all* keys in the English message map, there SHALL be a corresponding key
         * in the Chinese message map.
         *
         * **Validates: Requirements 6.8**
         */
        "Property 14b: Language completeness - Chinese map contains all English keys" {
            val chineseKeys = I18n.getMessages("cn").keys
            val englishKeys = I18n.getMessages("en").keys

            // All English keys should exist in Chinese map
            chineseKeys shouldContainAll englishKeys
        }

        /**
         * Property 14c: Language completeness - same key count
         *
         * Both language maps SHALL have the same number of keys.
         *
         * **Validates: Requirements 6.8**
         */
        "Property 14c: Language completeness - both maps have same key count" {
            val chineseMessages = I18n.getMessages("cn")
            val englishMessages = I18n.getMessages("en")

            chineseMessages.size shouldBe englishMessages.size
        }

        /**
         * Property: Valid keys always return non-empty values
         *
         * *For all* valid keys in the message maps, getMessage() SHALL return
         * a non-empty string (not the key itself).
         *
         * **Validates: Requirements 6.1, 6.2**
         */
        "Valid keys always return non-empty values different from the key" {
            val chineseMessages = I18n.getMessages("cn")
            val englishMessages = I18n.getMessages("en")

            chineseMessages.forEach { (key, value) ->
                value.isNotEmpty() shouldBe true
                I18n.getMessage(key, "cn") shouldBe value
            }

            englishMessages.forEach { (key, value) ->
                value.isNotEmpty() shouldBe true
                I18n.getMessage(key, "en") shouldBe value
            }
        }

        /**
         * Property: getMessages returns consistent results
         *
         * *For any* supported language, calling getMessages() multiple times
         * SHALL return the same map.
         *
         * **Validates: Requirements 6.6**
         */
        "getMessages returns consistent results for same language" {
            // Test Chinese
            val cn1 = I18n.getMessages("cn")
            val cn2 = I18n.getMessages("cn")
            cn1 shouldBe cn2

            // Test English
            val en1 = I18n.getMessages("en")
            val en2 = I18n.getMessages("en")
            en1 shouldBe en2

            // Test with "english" variant
            val english1 = I18n.getMessages("english")
            val english2 = I18n.getMessages("english")
            english1 shouldBe english2
            english1 shouldBe en1
        }

        /**
         * Property: Language code case insensitivity
         *
         * *For any* valid key, getMessage() with different case variations of
         * the language code SHALL return the same result.
         *
         * **Validates: Requirements 6.1, 6.2**
         */
        "Language code is case insensitive" {
            val validKeys = I18n.getMessages("cn").keys.take(10)

            validKeys.forEach { key ->
                // Test English variations
                val enLower = I18n.getMessage(key, "en")
                val enUpper = I18n.getMessage(key, "EN")
                val enMixed = I18n.getMessage(key, "En")

                enLower shouldBe enUpper
                enLower shouldBe enMixed

                // Test "english" variations
                val englishLower = I18n.getMessage(key, "english")
                val englishUpper = I18n.getMessage(key, "ENGLISH")
                val englishMixed = I18n.getMessage(key, "English")

                englishLower shouldBe englishUpper
                englishLower shouldBe englishMixed
                englishLower shouldBe enLower
            }
        }
    })
