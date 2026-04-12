package com.flowmate.autoxiaoer.action

import com.flowmate.autoxiaoer.util.Logger
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.unmockkObject

/**
 * Property-based tests for [ActionParser].
 *
 * Tests universal properties that should hold for all valid inputs.
 *
 * **Feature: unit-tests, Property 1: Valid action parsing preserves data**
 * **Validates: Requirements 1.1, 1.2**
 */
class ActionParserPropertyTest :
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
         * Property 1: Valid Tap action parsing preserves coordinates
         *
         * *For any* valid coordinates in range [0, 999], parsing a Tap action
         * SHALL produce an AgentAction.Tap with the same coordinate values.
         *
         * **Validates: Requirements 1.1**
         */
        "Property 1: Valid Tap action parsing preserves coordinates" {
            checkAll(100, Arb.int(0..999), Arb.int(0..999)) { x, y ->
                val input = """do(action="Tap", element=[$x, $y])"""
                val result = ActionParser.parse(input)

                result shouldBe AgentAction.Tap(x = x, y = y, message = null)
            }
        }

        /**
         * Property 1b: Valid Swipe action parsing preserves coordinates
         *
         * *For any* valid coordinates in range [0, 999], parsing a Swipe action
         * SHALL produce an AgentAction.Swipe with the same coordinate values.
         *
         * **Validates: Requirements 1.2**
         */
        "Property 1b: Valid Swipe action parsing preserves coordinates" {
            checkAll(
                100,
                Arb.int(0..999),
                Arb.int(0..999),
                Arb.int(0..999),
                Arb.int(0..999),
            ) { startX, startY, endX, endY ->
                val input = """do(action="Swipe", start=[$startX, $startY], end=[$endX, $endY])"""
                val result = ActionParser.parse(input)

                result shouldBe
                    AgentAction.Swipe(
                        startX = startX,
                        startY = startY,
                        endX = endX,
                        endY = endY,
                    )
            }
        }
    })

/**
 * Property-based tests for Type action text handling.
 *
 * **Feature: unit-tests, Property 2: Type action text round-trip**
 * **Validates: Requirements 1.3, 1.8**
 */
class TypeActionPropertyTest :
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
         * Property 2: Type action text round-trip
         *
         * *For any* text content (alphanumeric characters),
         * parsing a Type action SHALL correctly preserve the original text.
         *
         * Note: We test with safe characters that don't require escaping to verify
         * basic text preservation. Escaped quotes are tested separately.
         *
         * **Validates: Requirements 1.3**
         */
        "Property 2: Type action text is preserved for alphanumeric content" {
            checkAll(100, Arb.int(0..50)) { length ->
                val text = (1..length).map { ('a'..'z').random() }.joinToString("")
                val input = """do(action="Type", text="$text")"""
                val result = ActionParser.parse(input)

                result shouldBe AgentAction.Type(text = text)
            }
        }

        /**
         * Property 2b: Type action with escaped quotes round-trip
         *
         * *For any* text containing quotes, when properly escaped,
         * parsing SHALL correctly unescape and preserve the original text.
         *
         * **Validates: Requirements 1.8**
         */
        "Property 2b: Type action correctly unescapes quotes" {
            // Test with various quote patterns
            val testCases =
                listOf(
                    """He said \"Hello\"""" to """He said "Hello"""",
                    """Test \"one\" and \"two\"""" to """Test "one" and "two"""",
                    """No quotes here""" to """No quotes here""",
                    """Single \" quote""" to """Single " quote""",
                )

            testCases.forEach { (escaped, expected) ->
                val input = """do(action="Type", text="$escaped")"""
                val result = ActionParser.parse(input)

                result shouldBe AgentAction.Type(text = expected)
            }
        }
    })

/**
 * Property-based tests for coordinate validation.
 *
 * **Feature: unit-tests, Property 3: Out-of-range coordinates throw exception**
 * **Validates: Requirements 1.6**
 */
class CoordinateValidationPropertyTest :
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
         * Property 3: Out-of-range coordinates throw exception
         *
         * *For any* action string with coordinates outside [0, 999],
         * parsing SHALL throw CoordinateOutOfRangeException.
         *
         * **Validates: Requirements 1.6**
         */
        "Property 3: Coordinates above 999 throw CoordinateOutOfRangeException" {
            checkAll(100, Arb.int(1000..10000), Arb.int(0..999)) { invalidX, validY ->
                val input = """do(action="Tap", element=[$invalidX, $validY])"""

                val exception =
                    org.junit.jupiter.api.assertThrows<CoordinateOutOfRangeException> {
                        ActionParser.parse(input)
                    }

                exception.invalidCoordinates.any { it.name == "x" && it.value == invalidX } shouldBe true
            }
        }

        /**
         * Property 3b: Negative coordinates throw exception
         *
         * *For any* action string with negative coordinates,
         * parsing SHALL throw CoordinateOutOfRangeException.
         *
         * **Validates: Requirements 1.6**
         */
        "Property 3b: Negative coordinates throw CoordinateOutOfRangeException" {
            checkAll(100, Arb.int(-10000..-1), Arb.int(0..999)) { invalidX, validY ->
                val input = """do(action="Tap", element=[$invalidX, $validY])"""

                val exception =
                    org.junit.jupiter.api.assertThrows<CoordinateOutOfRangeException> {
                        ActionParser.parse(input)
                    }

                exception.invalidCoordinates.any { it.name == "x" && it.value == invalidX } shouldBe true
            }
        }

        /**
         * Property 3c: Swipe with out-of-range coordinates throws exception
         *
         * *For any* Swipe action with coordinates outside [0, 999],
         * parsing SHALL throw CoordinateOutOfRangeException.
         *
         * **Validates: Requirements 1.6**
         */
        "Property 3c: Swipe with out-of-range coordinates throws exception" {
            checkAll(100, Arb.int(1000..5000), Arb.int(0..999)) { invalidCoord, validCoord ->
                val input = """do(action="Swipe", start=[$validCoord, $validCoord], end=[$invalidCoord, $validCoord])"""

                val exception =
                    org.junit.jupiter.api.assertThrows<CoordinateOutOfRangeException> {
                        ActionParser.parse(input)
                    }

                exception.invalidCoordinates.any { it.name == "endX" && it.value == invalidCoord } shouldBe true
            }
        }
    })
