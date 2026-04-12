package com.flowmate.autoxiaoer.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll

/**
 * Property-based tests for [ModelResponseParser].
 *
 * Tests universal properties that should hold for all valid inputs.
 *
 * **Feature: unit-tests, Property 10: Thinking and action separation**
 * **Validates: Requirements 4.1, 4.2**
 */
class ThinkingActionSeparationPropertyTest :
    StringSpec({

        /**
         * Property 10: Thinking and action separation
         *
         * *For any* response containing thinking text followed by a do() action,
         * parsing SHALL correctly separate thinking from action.
         *
         * **Validates: Requirements 4.1**
         */
        "Property 10: Thinking and do action are correctly separated" {
            checkAll(100, Arb.int(0..999), Arb.int(0..999)) { x, y ->
                // Generate random thinking text (alphanumeric to avoid special chars)
                val thinkingTexts =
                    listOf(
                        "I need to tap the button",
                        "Analyzing the screen now",
                        "The user wants to proceed",
                        "Looking at the interface",
                        "I should click here",
                    )
                val thinking = thinkingTexts.random()
                val action = """do(action="Tap", element=[$x, $y])"""
                val content = "$thinking\n$action"

                val (parsedThinking, parsedAction) = ModelResponseParser.parseThinkingAndAction(content)

                parsedThinking shouldBe thinking
                parsedAction shouldBe action
            }
        }

        /**
         * Property 10b: Thinking and finish action separation
         *
         * *For any* response containing thinking text followed by a finish() action,
         * parsing SHALL correctly separate thinking from action.
         *
         * **Validates: Requirements 4.2**
         */
        "Property 10b: Thinking and finish action are correctly separated" {
            checkAll(100, Arb.int(1..50)) { messageLength ->
                val thinkingTexts =
                    listOf(
                        "Task completed successfully",
                        "All steps are done",
                        "The operation finished",
                        "Work is complete",
                        "Mission accomplished",
                    )
                val thinking = thinkingTexts.random()
                // Generate a simple message (alphanumeric)
                val message = (1..messageLength).map { ('a'..'z').random() }.joinToString("")
                val action = """finish(message="$message")"""
                val content = "$thinking\n$action"

                val (parsedThinking, parsedAction) = ModelResponseParser.parseThinkingAndAction(content)

                parsedThinking shouldBe thinking
                parsedAction shouldBe action
            }
        }

        /**
         * Property 10c: Action at start means empty thinking
         *
         * *For any* response starting with an action (no thinking),
         * parsing SHALL return empty thinking and the correct action.
         *
         * **Validates: Requirements 4.1**
         */
        "Property 10c: Action at start returns empty thinking" {
            checkAll(100, Arb.int(0..999), Arb.int(0..999)) { x, y ->
                val action = """do(action="Tap", element=[$x, $y])"""

                val (parsedThinking, parsedAction) = ModelResponseParser.parseThinkingAndAction(action)

                parsedThinking shouldBe ""
                parsedAction shouldBe action
            }
        }

        /**
         * Property 10d: Swipe action separation
         *
         * *For any* response containing thinking followed by a Swipe action,
         * parsing SHALL correctly separate thinking from action.
         *
         * **Validates: Requirements 4.1**
         */
        "Property 10d: Thinking and Swipe action are correctly separated" {
            checkAll(
                100,
                Arb.int(0..999),
                Arb.int(0..999),
                Arb.int(0..999),
                Arb.int(0..999),
            ) { startX, startY, endX, endY ->
                val thinking = "I need to scroll the screen"
                val action = """do(action="Swipe", start=[$startX, $startY], end=[$endX, $endY])"""
                val content = "$thinking\n$action"

                val (parsedThinking, parsedAction) = ModelResponseParser.parseThinkingAndAction(content)

                parsedThinking shouldBe thinking
                parsedAction shouldBe action
            }
        }
    })

/**
 * Property-based tests for nested parentheses handling.
 *
 * **Feature: unit-tests, Property 11: Nested parentheses handling**
 * **Validates: Requirements 4.3**
 */
class NestedParenthesesPropertyTest :
    StringSpec({

        /**
         * Property 11: Nested parentheses handling
         *
         * *For any* action containing text with balanced nested parentheses,
         * parsing SHALL correctly extract the complete action.
         *
         * **Validates: Requirements 4.3**
         */
        "Property 11: Text with single level nested parentheses is handled correctly" {
            checkAll(100, Arb.int(1..20)) { wordLength ->
                val word = (1..wordLength).map { ('a'..'z').random() }.joinToString("")
                val thinking = "Typing text with parentheses"
                val textWithParens = "Hello ($word)"
                val action = """do(action="Type", text="$textWithParens")"""
                val content = "$thinking\n$action"

                val (parsedThinking, parsedAction) = ModelResponseParser.parseThinkingAndAction(content)

                parsedThinking shouldBe thinking
                parsedAction shouldBe action
                // Verify the action contains the nested parentheses
                parsedAction shouldContain "($word)"
            }
        }

        /**
         * Property 11b: Multiple nested parentheses
         *
         * *For any* action containing text with multiple nested parentheses,
         * parsing SHALL correctly extract the complete action.
         *
         * **Validates: Requirements 4.3**
         */
        "Property 11b: Text with multiple nested parentheses is handled correctly" {
            checkAll(100, Arb.int(1..10), Arb.int(1..10)) { len1, len2 ->
                val word1 = (1..len1).map { ('a'..'z').random() }.joinToString("")
                val word2 = (1..len2).map { ('a'..'z').random() }.joinToString("")
                val thinking = "Complex text input"
                val textWithParens = "func($word1) and ($word2)"
                val action = """do(action="Type", text="$textWithParens")"""
                val content = "$thinking\n$action"

                val (parsedThinking, parsedAction) = ModelResponseParser.parseThinkingAndAction(content)

                parsedThinking shouldBe thinking
                parsedAction shouldBe action
                // Verify both parentheses groups are preserved
                parsedAction shouldContain "($word1)"
                parsedAction shouldContain "($word2)"
            }
        }

        /**
         * Property 11c: Deeply nested parentheses
         *
         * *For any* action containing text with deeply nested parentheses,
         * parsing SHALL correctly extract the complete action.
         *
         * **Validates: Requirements 4.3**
         */
        "Property 11c: Text with deeply nested parentheses is handled correctly" {
            checkAll(100, Arb.int(1..5), Arb.int(1..5)) { a, b ->
                val thinking = "Mathematical expression"
                val textWithParens = "(($a + $b))"
                val action = """do(action="Type", text="$textWithParens")"""
                val content = "$thinking\n$action"

                val (parsedThinking, parsedAction) = ModelResponseParser.parseThinkingAndAction(content)

                parsedThinking shouldBe thinking
                parsedAction shouldBe action
                // Verify nested structure is preserved
                parsedAction shouldContain "(($a + $b))"
            }
        }

        /**
         * Property 11d: Finish action with nested parentheses
         *
         * *For any* finish action containing message with nested parentheses,
         * parsing SHALL correctly extract the complete action.
         *
         * **Validates: Requirements 4.3**
         */
        "Property 11d: Finish action with nested parentheses is handled correctly" {
            checkAll(100, Arb.int(1..15)) { wordLength ->
                val word = (1..wordLength).map { ('a'..'z').random() }.joinToString("")
                val thinking = "Task complete"
                val messageWithParens = "Done ($word)"
                val action = """finish(message="$messageWithParens")"""
                val content = "$thinking\n$action"

                val (parsedThinking, parsedAction) = ModelResponseParser.parseThinkingAndAction(content)

                parsedThinking shouldBe thinking
                parsedAction shouldBe action
                // Verify parentheses in message are preserved
                parsedAction shouldContain "($word)"
            }
        }

        /**
         * Property 11e: Coordinates are not affected by parentheses handling
         *
         * *For any* valid coordinates, the action parsing should correctly
         * handle the square brackets without confusion with parentheses.
         *
         * **Validates: Requirements 4.3**
         */
        "Property 11e: Coordinates with brackets are handled correctly" {
            checkAll(100, Arb.int(0..999), Arb.int(0..999)) { x, y ->
                val thinking = "Tapping on element"
                val action = """do(action="Tap", element=[$x, $y])"""
                val content = "$thinking\n$action"

                val (parsedThinking, parsedAction) = ModelResponseParser.parseThinkingAndAction(content)

                parsedThinking shouldBe thinking
                parsedAction shouldBe action
                // Verify coordinates are preserved
                parsedAction shouldContain "[$x, $y]"
            }
        }
    })
