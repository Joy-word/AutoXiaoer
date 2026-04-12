package com.flowmate.autoxiaoer.util

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlin.random.Random

/**
 * Property-based tests for [HumanizedSwipeGenerator].
 *
 * Tests universal properties that should hold for all valid swipe path generations.
 *
 * **Feature: unit-tests, Property 12: Path validity**
 * **Validates: Requirements 5.1, 5.2, 5.3, 5.4**
 */
class HumanizedSwipeGeneratorPropertyTest :
    StringSpec({

        /**
         * Property 12a: Generated path has at least 2 points
         *
         * *For any* valid swipe parameters (start, end within screen bounds),
         * the generated path SHALL have at least 2 points.
         *
         * **Validates: Requirements 5.1**
         */
        "Property 12a: generatePath always returns at least 2 points" {
            checkAll(
                100,
                Arb.int(0..1000), // startX
                Arb.int(0..2000), // startY
                Arb.int(0..1000), // endX
                Arb.int(0..2000), // endY
                Arb.int(720..3840), // screenWidth
                Arb.int(1280..4320), // screenHeight
            ) { startX, startY, endX, endY, screenWidth, screenHeight ->
                // Ensure coordinates are within screen bounds
                val clampedStartX = startX.coerceIn(0, screenWidth - 1)
                val clampedStartY = startY.coerceIn(0, screenHeight - 1)
                val clampedEndX = endX.coerceIn(0, screenWidth - 1)
                val clampedEndY = endY.coerceIn(0, screenHeight - 1)

                val generator = HumanizedSwipeGenerator(Random(12345))
                val path =
                    generator.generatePath(
                        clampedStartX,
                        clampedStartY,
                        clampedEndX,
                        clampedEndY,
                        screenWidth,
                        screenHeight,
                    )

                path.points.size shouldBeGreaterThanOrEqual 2
            }
        }

        /**
         * Property 12b: First point is the start coordinate
         *
         * *For any* valid swipe parameters, the first point of the generated path
         * SHALL be the start coordinate.
         *
         * **Validates: Requirements 5.2**
         */
        "Property 12b: generatePath first point is start coordinate" {
            checkAll(
                100,
                Arb.int(0..1000), // startX
                Arb.int(0..2000), // startY
                Arb.int(0..1000), // endX
                Arb.int(0..2000), // endY
                Arb.int(720..3840), // screenWidth
                Arb.int(1280..4320), // screenHeight
            ) { startX, startY, endX, endY, screenWidth, screenHeight ->
                val clampedStartX = startX.coerceIn(0, screenWidth - 1)
                val clampedStartY = startY.coerceIn(0, screenHeight - 1)
                val clampedEndX = endX.coerceIn(0, screenWidth - 1)
                val clampedEndY = endY.coerceIn(0, screenHeight - 1)

                val generator = HumanizedSwipeGenerator(Random(12345))
                val path =
                    generator.generatePath(
                        clampedStartX,
                        clampedStartY,
                        clampedEndX,
                        clampedEndY,
                        screenWidth,
                        screenHeight,
                    )

                path.points.first().x shouldBe clampedStartX
                path.points.first().y shouldBe clampedStartY
            }
        }

        /**
         * Property 12c: Last point is the end coordinate
         *
         * *For any* valid swipe parameters, the last point of the generated path
         * SHALL be the end coordinate.
         *
         * **Validates: Requirements 5.3**
         */
        "Property 12c: generatePath last point is end coordinate" {
            checkAll(
                100,
                Arb.int(0..1000), // startX
                Arb.int(0..2000), // startY
                Arb.int(0..1000), // endX
                Arb.int(0..2000), // endY
                Arb.int(720..3840), // screenWidth
                Arb.int(1280..4320), // screenHeight
            ) { startX, startY, endX, endY, screenWidth, screenHeight ->
                val clampedStartX = startX.coerceIn(0, screenWidth - 1)
                val clampedStartY = startY.coerceIn(0, screenHeight - 1)
                val clampedEndX = endX.coerceIn(0, screenWidth - 1)
                val clampedEndY = endY.coerceIn(0, screenHeight - 1)

                val generator = HumanizedSwipeGenerator(Random(12345))
                val path =
                    generator.generatePath(
                        clampedStartX,
                        clampedStartY,
                        clampedEndX,
                        clampedEndY,
                        screenWidth,
                        screenHeight,
                    )

                path.points.last().x shouldBe clampedEndX
                path.points.last().y shouldBe clampedEndY
            }
        }

        /**
         * Property 12d: All points are within screen bounds
         *
         * *For any* valid swipe parameters, all points in the generated path
         * SHALL be within screen bounds (0 <= x < screenWidth, 0 <= y < screenHeight).
         *
         * **Validates: Requirements 5.4**
         */
        "Property 12d: generatePath all points within screen bounds" {
            checkAll(
                100,
                Arb.int(0..1000), // startX
                Arb.int(0..2000), // startY
                Arb.int(0..1000), // endX
                Arb.int(0..2000), // endY
                Arb.int(720..3840), // screenWidth
                Arb.int(1280..4320), // screenHeight
            ) { startX, startY, endX, endY, screenWidth, screenHeight ->
                val clampedStartX = startX.coerceIn(0, screenWidth - 1)
                val clampedStartY = startY.coerceIn(0, screenHeight - 1)
                val clampedEndX = endX.coerceIn(0, screenWidth - 1)
                val clampedEndY = endY.coerceIn(0, screenHeight - 1)

                val generator = HumanizedSwipeGenerator(Random(12345))
                val path =
                    generator.generatePath(
                        clampedStartX,
                        clampedStartY,
                        clampedEndX,
                        clampedEndY,
                        screenWidth,
                        screenHeight,
                    )

                path.points.forEach { point ->
                    point.x shouldBeGreaterThanOrEqual 0
                    point.x shouldBeLessThan screenWidth
                    point.y shouldBeGreaterThanOrEqual 0
                    point.y shouldBeLessThan screenHeight
                }
            }
        }

        /**
         * Property 12e: Linear path has at least 2 points
         *
         * *For any* valid swipe parameters, the generated linear path
         * SHALL have at least 2 points.
         *
         * **Validates: Requirements 5.1**
         */
        "Property 12e: generateLinearPath always returns at least 2 points" {
            checkAll(
                100,
                Arb.int(0..1000), // startX
                Arb.int(0..2000), // startY
                Arb.int(0..1000), // endX
                Arb.int(0..2000), // endY
                Arb.int(720..3840), // screenWidth
                Arb.int(1280..4320), // screenHeight
            ) { startX, startY, endX, endY, screenWidth, screenHeight ->
                val clampedStartX = startX.coerceIn(0, screenWidth - 1)
                val clampedStartY = startY.coerceIn(0, screenHeight - 1)
                val clampedEndX = endX.coerceIn(0, screenWidth - 1)
                val clampedEndY = endY.coerceIn(0, screenHeight - 1)

                val generator = HumanizedSwipeGenerator(Random(12345))
                val path =
                    generator.generateLinearPath(
                        clampedStartX,
                        clampedStartY,
                        clampedEndX,
                        clampedEndY,
                        screenWidth,
                        screenHeight,
                    )

                path.points.size shouldBeGreaterThanOrEqual 2
            }
        }

        /**
         * Property 12f: Linear path first point is start coordinate
         *
         * *For any* valid swipe parameters, the first point of the linear path
         * SHALL be the start coordinate.
         *
         * **Validates: Requirements 5.2**
         */
        "Property 12f: generateLinearPath first point is start coordinate" {
            checkAll(
                100,
                Arb.int(0..1000), // startX
                Arb.int(0..2000), // startY
                Arb.int(0..1000), // endX
                Arb.int(0..2000), // endY
                Arb.int(720..3840), // screenWidth
                Arb.int(1280..4320), // screenHeight
            ) { startX, startY, endX, endY, screenWidth, screenHeight ->
                val clampedStartX = startX.coerceIn(0, screenWidth - 1)
                val clampedStartY = startY.coerceIn(0, screenHeight - 1)
                val clampedEndX = endX.coerceIn(0, screenWidth - 1)
                val clampedEndY = endY.coerceIn(0, screenHeight - 1)

                val generator = HumanizedSwipeGenerator(Random(12345))
                val path =
                    generator.generateLinearPath(
                        clampedStartX,
                        clampedStartY,
                        clampedEndX,
                        clampedEndY,
                        screenWidth,
                        screenHeight,
                    )

                path.points.first().x shouldBe clampedStartX
                path.points.first().y shouldBe clampedStartY
            }
        }

        /**
         * Property 12g: Linear path last point is end coordinate
         *
         * *For any* valid swipe parameters, the last point of the linear path
         * SHALL be the end coordinate.
         *
         * **Validates: Requirements 5.3**
         */
        "Property 12g: generateLinearPath last point is end coordinate" {
            checkAll(
                100,
                Arb.int(0..1000), // startX
                Arb.int(0..2000), // startY
                Arb.int(0..1000), // endX
                Arb.int(0..2000), // endY
                Arb.int(720..3840), // screenWidth
                Arb.int(1280..4320), // screenHeight
            ) { startX, startY, endX, endY, screenWidth, screenHeight ->
                val clampedStartX = startX.coerceIn(0, screenWidth - 1)
                val clampedStartY = startY.coerceIn(0, screenHeight - 1)
                val clampedEndX = endX.coerceIn(0, screenWidth - 1)
                val clampedEndY = endY.coerceIn(0, screenHeight - 1)

                val generator = HumanizedSwipeGenerator(Random(12345))
                val path =
                    generator.generateLinearPath(
                        clampedStartX,
                        clampedStartY,
                        clampedEndX,
                        clampedEndY,
                        screenWidth,
                        screenHeight,
                    )

                path.points.last().x shouldBe clampedEndX
                path.points.last().y shouldBe clampedEndY
            }
        }

        /**
         * Property 12h: Linear path all points within screen bounds
         *
         * *For any* valid swipe parameters, all points in the linear path
         * SHALL be within screen bounds.
         *
         * **Validates: Requirements 5.4**
         */
        "Property 12h: generateLinearPath all points within screen bounds" {
            checkAll(
                100,
                Arb.int(0..1000), // startX
                Arb.int(0..2000), // startY
                Arb.int(0..1000), // endX
                Arb.int(0..2000), // endY
                Arb.int(720..3840), // screenWidth
                Arb.int(1280..4320), // screenHeight
            ) { startX, startY, endX, endY, screenWidth, screenHeight ->
                val clampedStartX = startX.coerceIn(0, screenWidth - 1)
                val clampedStartY = startY.coerceIn(0, screenHeight - 1)
                val clampedEndX = endX.coerceIn(0, screenWidth - 1)
                val clampedEndY = endY.coerceIn(0, screenHeight - 1)

                val generator = HumanizedSwipeGenerator(Random(12345))
                val path =
                    generator.generateLinearPath(
                        clampedStartX,
                        clampedStartY,
                        clampedEndX,
                        clampedEndY,
                        screenWidth,
                        screenHeight,
                    )

                path.points.forEach { point ->
                    point.x shouldBeGreaterThanOrEqual 0
                    point.x shouldBeLessThan screenWidth
                    point.y shouldBeGreaterThanOrEqual 0
                    point.y shouldBeLessThan screenHeight
                }
            }
        }

        /**
         * Property: Duration is always positive
         *
         * *For any* valid swipe parameters, the generated path duration
         * SHALL be positive.
         *
         * **Validates: Requirements 5.1**
         */
        "Duration is always positive" {
            checkAll(
                100,
                Arb.int(0..1000), // startX
                Arb.int(0..2000), // startY
                Arb.int(0..1000), // endX
                Arb.int(0..2000), // endY
                Arb.int(720..3840), // screenWidth
                Arb.int(1280..4320), // screenHeight
            ) { startX, startY, endX, endY, screenWidth, screenHeight ->
                val clampedStartX = startX.coerceIn(0, screenWidth - 1)
                val clampedStartY = startY.coerceIn(0, screenHeight - 1)
                val clampedEndX = endX.coerceIn(0, screenWidth - 1)
                val clampedEndY = endY.coerceIn(0, screenHeight - 1)

                val generator = HumanizedSwipeGenerator(Random(12345))
                val path =
                    generator.generatePath(
                        clampedStartX,
                        clampedStartY,
                        clampedEndX,
                        clampedEndY,
                        screenWidth,
                        screenHeight,
                    )

                path.durationMs shouldBeGreaterThanOrEqual 1
            }
        }

        /**
         * Property: Duration is within bounds
         *
         * *For any* valid swipe parameters, the generated path duration
         * SHALL be within [150, 1500] milliseconds.
         *
         * **Validates: Requirements 5.1**
         */
        "Duration is within bounds (150-1500ms)" {
            checkAll(
                100,
                Arb.int(0..1000), // startX
                Arb.int(0..2000), // startY
                Arb.int(0..1000), // endX
                Arb.int(0..2000), // endY
                Arb.int(720..3840), // screenWidth
                Arb.int(1280..4320), // screenHeight
            ) { startX, startY, endX, endY, screenWidth, screenHeight ->
                val clampedStartX = startX.coerceIn(0, screenWidth - 1)
                val clampedStartY = startY.coerceIn(0, screenHeight - 1)
                val clampedEndX = endX.coerceIn(0, screenWidth - 1)
                val clampedEndY = endY.coerceIn(0, screenHeight - 1)

                val generator = HumanizedSwipeGenerator(Random(12345))
                val path =
                    generator.generatePath(
                        clampedStartX,
                        clampedStartY,
                        clampedEndX,
                        clampedEndY,
                        screenWidth,
                        screenHeight,
                    )

                path.durationMs shouldBeGreaterThanOrEqual 150
                path.durationMs shouldBeLessThan 1501
            }
        }
    })
