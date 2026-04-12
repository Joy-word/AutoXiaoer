package com.flowmate.autoxiaoer.util

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlin.math.abs

/**
 * Property-based tests for [CoordinateConverter].
 *
 * Tests universal properties that should hold for all valid coordinate conversions.
 *
 * **Feature: unit-tests, Property 5: Coordinate conversion round-trip**
 * **Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.7**
 */
class CoordinateConverterPropertyTest :
    StringSpec({

        /**
         * Calculates the maximum expected round-trip error for a given screen dimension.
         *
         * The error comes from two integer divisions:
         * 1. relative -> absolute: loses up to (RELATIVE_MAX / screenDim) precision
         * 2. absolute -> relative: loses up to (screenDim / RELATIVE_MAX) precision
         *
         * For typical screen sizes (720+), the error is ≤1.
         * For smaller dimensions, the error can be larger.
         */
        fun maxRoundTripError(screenDimension: Int): Int {
            // The maximum error is ceil(RELATIVE_MAX / screenDimension)
            // This accounts for the quantization step size
            return (CoordinateConverter.RELATIVE_MAX + screenDimension - 1) / screenDimension
        }

        /**
         * Property 5: Coordinate conversion round-trip for X coordinate
         *
         * *For any* relative X coordinate in [0, 999] and screen width > 0,
         * converting to absolute then back to relative SHALL produce a value
         * within the expected tolerance (based on screen dimension).
         *
         * **Validates: Requirements 2.1, 2.3, 2.7**
         */
        "Property 5a: X coordinate round-trip preserves value within tolerance" {
            checkAll(100, Arb.int(0..999), Arb.int(100..4000)) { relativeX, screenWidth ->
                val absoluteX = CoordinateConverter.toAbsoluteX(relativeX, screenWidth)
                val roundTrip = CoordinateConverter.toRelativeX(absoluteX, screenWidth)

                // Tolerance depends on screen dimension - smaller screens have larger quantization error
                val maxError = maxRoundTripError(screenWidth)
                abs(roundTrip - relativeX) shouldBeLessThan (maxError + 1)
            }
        }

        /**
         * Property 5b: Coordinate conversion round-trip for Y coordinate
         *
         * *For any* relative Y coordinate in [0, 999] and screen height > 0,
         * converting to absolute then back to relative SHALL produce a value
         * within the expected tolerance (based on screen dimension).
         *
         * **Validates: Requirements 2.2, 2.4, 2.7**
         */
        "Property 5b: Y coordinate round-trip preserves value within tolerance" {
            checkAll(100, Arb.int(0..999), Arb.int(100..4000)) { relativeY, screenHeight ->
                val absoluteY = CoordinateConverter.toAbsoluteY(relativeY, screenHeight)
                val roundTrip = CoordinateConverter.toRelativeY(absoluteY, screenHeight)

                // Tolerance depends on screen dimension - smaller screens have larger quantization error
                val maxError = maxRoundTripError(screenHeight)
                abs(roundTrip - relativeY) shouldBeLessThan (maxError + 1)
            }
        }

        /**
         * Property 5c: Combined coordinate round-trip
         *
         * *For any* relative coordinates in [0, 999] and screen dimensions > 0,
         * converting to absolute then back to relative SHALL produce values
         * within the expected tolerance for both X and Y.
         *
         * **Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.7**
         */
        "Property 5c: Combined coordinate round-trip preserves values within tolerance" {
            checkAll(
                100,
                Arb.int(0..999),
                Arb.int(0..999),
                Arb.int(100..4000),
                Arb.int(100..4000),
            ) { relativeX, relativeY, screenWidth, screenHeight ->
                val (absoluteX, absoluteY) =
                    CoordinateConverter.toAbsolute(
                        relativeX,
                        relativeY,
                        screenWidth,
                        screenHeight,
                    )
                val roundTripX = CoordinateConverter.toRelativeX(absoluteX, screenWidth)
                val roundTripY = CoordinateConverter.toRelativeY(absoluteY, screenHeight)

                val maxErrorX = maxRoundTripError(screenWidth)
                val maxErrorY = maxRoundTripError(screenHeight)
                abs(roundTripX - relativeX) shouldBeLessThan (maxErrorX + 1)
                abs(roundTripY - relativeY) shouldBeLessThan (maxErrorY + 1)
            }
        }

        /**
         * Property 6a: Boundary coordinate 0 always maps to 0
         *
         * *For any* screen dimension, relative coordinate 0 SHALL map to absolute 0.
         *
         * **Validates: Requirements 2.5**
         */
        "Property 6a: Relative 0 always maps to absolute 0" {
            checkAll(100, Arb.int(100..4000)) { screenDimension ->
                CoordinateConverter.toAbsoluteX(0, screenDimension) shouldBe 0
                CoordinateConverter.toAbsoluteY(0, screenDimension) shouldBe 0
            }
        }

        /**
         * Property 6b: Boundary coordinate 999 maps to value close to screen dimension
         *
         * *For any* screen dimension, relative coordinate 999 SHALL map to a value
         * close to (but less than) the screen dimension.
         *
         * **Validates: Requirements 2.6**
         */
        "Property 6b: Relative 999 maps to value close to but less than screen dimension" {
            checkAll(100, Arb.int(100..4000)) { screenDimension ->
                val absoluteX = CoordinateConverter.toAbsoluteX(999, screenDimension)
                val absoluteY = CoordinateConverter.toAbsoluteY(999, screenDimension)

                // Result should be less than screen dimension
                absoluteX shouldBeLessThan screenDimension
                absoluteY shouldBeLessThan screenDimension

                // Result should be at least 99% of screen dimension
                absoluteX shouldBeGreaterThanOrEqual (screenDimension * 99 / 100)
                absoluteY shouldBeGreaterThanOrEqual (screenDimension * 99 / 100)
            }
        }

        /**
         * Property: Absolute coordinates are always non-negative
         *
         * *For any* valid relative coordinate in [0, 999] and positive screen dimension,
         * the absolute coordinate SHALL be non-negative.
         *
         * **Validates: Requirements 2.1, 2.2**
         */
        "Absolute coordinates are always non-negative" {
            checkAll(100, Arb.int(0..999), Arb.int(100..4000)) { relative, screenDimension ->
                CoordinateConverter.toAbsoluteX(relative, screenDimension) shouldBeGreaterThanOrEqual 0
                CoordinateConverter.toAbsoluteY(relative, screenDimension) shouldBeGreaterThanOrEqual 0
            }
        }

        /**
         * Property: Absolute coordinates are always less than screen dimension
         *
         * *For any* valid relative coordinate in [0, 999] and positive screen dimension,
         * the absolute coordinate SHALL be less than the screen dimension.
         *
         * **Validates: Requirements 2.1, 2.2**
         */
        "Absolute coordinates are always less than screen dimension" {
            checkAll(100, Arb.int(0..999), Arb.int(100..4000)) { relative, screenDimension ->
                CoordinateConverter.toAbsoluteX(relative, screenDimension) shouldBeLessThan screenDimension
                CoordinateConverter.toAbsoluteY(relative, screenDimension) shouldBeLessThan screenDimension
            }
        }

        /**
         * Property: Monotonicity - larger relative coordinates produce larger absolute coordinates
         *
         * *For any* two relative coordinates where r1 < r2, the absolute coordinate
         * for r1 SHALL be less than or equal to the absolute coordinate for r2.
         *
         * **Validates: Requirements 2.1, 2.2**
         */
        "Larger relative coordinates produce larger or equal absolute coordinates" {
            checkAll(100, Arb.int(0..998), Arb.int(100..4000)) { relative1, screenDimension ->
                val relative2 = relative1 + 1

                val absolute1X = CoordinateConverter.toAbsoluteX(relative1, screenDimension)
                val absolute2X = CoordinateConverter.toAbsoluteX(relative2, screenDimension)

                absolute2X shouldBeGreaterThanOrEqual absolute1X
            }
        }
    })
