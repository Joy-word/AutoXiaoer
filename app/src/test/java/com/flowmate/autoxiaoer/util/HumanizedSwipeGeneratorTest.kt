package com.flowmate.autoxiaoer.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * Unit tests for [HumanizedSwipeGenerator].
 *
 * Tests swipe path generation including point count, start/end correctness,
 * and screen boundary constraints.
 *
 * _Requirements: 5.1, 5.2, 5.3, 5.4_
 */
class HumanizedSwipeGeneratorTest {
    private lateinit var generator: HumanizedSwipeGenerator

    // Fixed seed for reproducible tests
    private val fixedSeed = 12345L

    @BeforeEach
    fun setup() {
        // Use fixed Random seed for reproducible tests
        generator = HumanizedSwipeGenerator(Random(fixedSeed))
    }

    // ==================== generatePath Basic Tests ====================
    // _Requirements: 5.1_

    @Test
    fun `generatePath_validInput_returnsPathWithAtLeast2Points`() {
        // Given
        val startX = 100
        val startY = 500
        val endX = 100
        val endY = 1500
        val screenWidth = 1080
        val screenHeight = 2400

        // When
        val path = generator.generatePath(startX, startY, endX, endY, screenWidth, screenHeight)

        // Then
        assertTrue(path.points.size >= 2, "Path should have at least 2 points")
    }

    @Test
    fun `generatePath_validInput_returnsPathWith20Points`() {
        // Given - default point count is 20
        val startX = 100
        val startY = 500
        val endX = 100
        val endY = 1500
        val screenWidth = 1080
        val screenHeight = 2400

        // When
        val path = generator.generatePath(startX, startY, endX, endY, screenWidth, screenHeight)

        // Then
        assertEquals(20, path.points.size, "Path should have exactly 20 points (default)")
    }

    @Test
    fun `generatePath_validInput_returnsPositiveDuration`() {
        // Given
        val startX = 100
        val startY = 500
        val endX = 100
        val endY = 1500
        val screenWidth = 1080
        val screenHeight = 2400

        // When
        val path = generator.generatePath(startX, startY, endX, endY, screenWidth, screenHeight)

        // Then
        assertTrue(path.durationMs > 0, "Duration should be positive")
    }

    // ==================== generatePath Start Point Tests ====================
    // _Requirements: 5.2_

    @Test
    fun `generatePath_validInput_firstPointIsStartCoordinate`() {
        // Given
        val startX = 100
        val startY = 500
        val endX = 100
        val endY = 1500
        val screenWidth = 1080
        val screenHeight = 2400

        // When
        val path = generator.generatePath(startX, startY, endX, endY, screenWidth, screenHeight)

        // Then
        assertEquals(startX, path.points.first().x, "First point X should be startX")
        assertEquals(startY, path.points.first().y, "First point Y should be startY")
    }

    @Test
    fun `generatePath_differentStartCoordinates_firstPointMatchesStart`() {
        // Given
        val startX = 540
        val startY = 1200
        val endX = 800
        val endY = 400
        val screenWidth = 1080
        val screenHeight = 2400

        // When
        val path = generator.generatePath(startX, startY, endX, endY, screenWidth, screenHeight)

        // Then
        assertEquals(startX, path.points.first().x)
        assertEquals(startY, path.points.first().y)
    }

    // ==================== generatePath End Point Tests ====================
    // _Requirements: 5.3_

    @Test
    fun `generatePath_validInput_lastPointIsEndCoordinate`() {
        // Given
        val startX = 100
        val startY = 500
        val endX = 100
        val endY = 1500
        val screenWidth = 1080
        val screenHeight = 2400

        // When
        val path = generator.generatePath(startX, startY, endX, endY, screenWidth, screenHeight)

        // Then
        assertEquals(endX, path.points.last().x, "Last point X should be endX")
        assertEquals(endY, path.points.last().y, "Last point Y should be endY")
    }

    @Test
    fun `generatePath_differentEndCoordinates_lastPointMatchesEnd`() {
        // Given
        val startX = 100
        val startY = 100
        val endX = 900
        val endY = 2000
        val screenWidth = 1080
        val screenHeight = 2400

        // When
        val path = generator.generatePath(startX, startY, endX, endY, screenWidth, screenHeight)

        // Then
        assertEquals(endX, path.points.last().x)
        assertEquals(endY, path.points.last().y)
    }

    // ==================== generatePath Screen Boundary Tests ====================
    // _Requirements: 5.4_

    @Test
    fun `generatePath_validInput_allPointsWithinScreenBounds`() {
        // Given
        val startX = 100
        val startY = 500
        val endX = 100
        val endY = 1500
        val screenWidth = 1080
        val screenHeight = 2400

        // When
        val path = generator.generatePath(startX, startY, endX, endY, screenWidth, screenHeight)

        // Then
        path.points.forEachIndexed { index, point ->
            assertTrue(point.x >= 0, "Point $index X should be >= 0, was ${point.x}")
            assertTrue(point.x < screenWidth, "Point $index X should be < $screenWidth, was ${point.x}")
            assertTrue(point.y >= 0, "Point $index Y should be >= 0, was ${point.y}")
            assertTrue(point.y < screenHeight, "Point $index Y should be < $screenHeight, was ${point.y}")
        }
    }

    @Test
    fun `generatePath_nearEdgeCoordinates_allPointsWithinScreenBounds`() {
        // Given - coordinates near screen edges
        val startX = 10
        val startY = 10
        val endX = 1070
        val endY = 2390
        val screenWidth = 1080
        val screenHeight = 2400

        // When
        val path = generator.generatePath(startX, startY, endX, endY, screenWidth, screenHeight)

        // Then
        path.points.forEach { point ->
            assertTrue(point.x >= 0 && point.x < screenWidth, "X out of bounds: ${point.x}")
            assertTrue(point.y >= 0 && point.y < screenHeight, "Y out of bounds: ${point.y}")
        }
    }

    // ==================== generateLinearPath Basic Tests ====================
    // _Requirements: 5.1_

    @Test
    fun `generateLinearPath_validInput_returnsPathWithAtLeast2Points`() {
        // Given
        val startX = 100
        val startY = 500
        val endX = 100
        val endY = 1500
        val screenWidth = 1080
        val screenHeight = 2400

        // When
        val path = generator.generateLinearPath(startX, startY, endX, endY, screenWidth, screenHeight)

        // Then
        assertTrue(path.points.size >= 2, "Path should have at least 2 points")
    }

    @Test
    fun `generateLinearPath_validInput_returnsPathWith20Points`() {
        // Given
        val startX = 100
        val startY = 500
        val endX = 100
        val endY = 1500
        val screenWidth = 1080
        val screenHeight = 2400

        // When
        val path = generator.generateLinearPath(startX, startY, endX, endY, screenWidth, screenHeight)

        // Then
        assertEquals(20, path.points.size, "Path should have exactly 20 points (default)")
    }

    // ==================== generateLinearPath Start/End Point Tests ====================
    // _Requirements: 5.2, 5.3_

    @Test
    fun `generateLinearPath_validInput_firstPointIsStartCoordinate`() {
        // Given
        val startX = 200
        val startY = 600
        val endX = 800
        val endY = 1800
        val screenWidth = 1080
        val screenHeight = 2400

        // When
        val path = generator.generateLinearPath(startX, startY, endX, endY, screenWidth, screenHeight)

        // Then
        assertEquals(startX, path.points.first().x)
        assertEquals(startY, path.points.first().y)
    }

    @Test
    fun `generateLinearPath_validInput_lastPointIsEndCoordinate`() {
        // Given
        val startX = 200
        val startY = 600
        val endX = 800
        val endY = 1800
        val screenWidth = 1080
        val screenHeight = 2400

        // When
        val path = generator.generateLinearPath(startX, startY, endX, endY, screenWidth, screenHeight)

        // Then
        assertEquals(endX, path.points.last().x)
        assertEquals(endY, path.points.last().y)
    }

    // ==================== generateLinearPath Screen Boundary Tests ====================
    // _Requirements: 5.4_

    @Test
    fun `generateLinearPath_validInput_allPointsWithinScreenBounds`() {
        // Given
        val startX = 100
        val startY = 500
        val endX = 900
        val endY = 2000
        val screenWidth = 1080
        val screenHeight = 2400

        // When
        val path = generator.generateLinearPath(startX, startY, endX, endY, screenWidth, screenHeight)

        // Then
        path.points.forEach { point ->
            assertTrue(point.x >= 0 && point.x < screenWidth, "X out of bounds: ${point.x}")
            assertTrue(point.y >= 0 && point.y < screenHeight, "Y out of bounds: ${point.y}")
        }
    }

    // ==================== Duration Calculation Tests ====================

    @Test
    fun `calculateDuration_shortDistance_returnsMinDuration`() {
        // Given - very short distance
        val distance = 10.0

        // When
        val duration = generator.calculateDuration(distance)

        // Then - should be at least MIN_DURATION_MS (150)
        assertTrue(duration >= 150, "Duration should be at least 150ms")
    }

    @Test
    fun `calculateDuration_longDistance_returnsMaxDuration`() {
        // Given - very long distance
        val distance = 10000.0

        // When
        val duration = generator.calculateDuration(distance)

        // Then - should be at most MAX_DURATION_MS (1500)
        assertTrue(duration <= 1500, "Duration should be at most 1500ms")
    }

    @Test
    fun `calculateDuration_mediumDistance_returnsDurationInRange`() {
        // Given - medium distance
        val distance = 500.0

        // When
        val duration = generator.calculateDuration(distance)

        // Then - should be between min and max
        assertTrue(duration in 150..1500, "Duration should be in range [150, 1500]")
    }

    // ==================== Different Screen Sizes Tests ====================

    @Test
    fun `generatePath_smallScreen_allPointsWithinBounds`() {
        // Given - small screen (720p)
        val startX = 100
        val startY = 200
        val endX = 600
        val endY = 1000
        val screenWidth = 720
        val screenHeight = 1280

        // When
        val path = generator.generatePath(startX, startY, endX, endY, screenWidth, screenHeight)

        // Then
        path.points.forEach { point ->
            assertTrue(point.x >= 0 && point.x < screenWidth)
            assertTrue(point.y >= 0 && point.y < screenHeight)
        }
    }

    @Test
    fun `generatePath_largeScreen_allPointsWithinBounds`() {
        // Given - large screen (4K)
        val startX = 500
        val startY = 1000
        val endX = 3000
        val endY = 3500
        val screenWidth = 3840
        val screenHeight = 4320

        // When
        val path = generator.generatePath(startX, startY, endX, endY, screenWidth, screenHeight)

        // Then
        path.points.forEach { point ->
            assertTrue(point.x >= 0 && point.x < screenWidth)
            assertTrue(point.y >= 0 && point.y < screenHeight)
        }
    }

    // ==================== Swipe Direction Tests ====================

    @Test
    fun `generatePath_verticalSwipeDown_generatesValidPath`() {
        // Given - vertical swipe down
        val startX = 540
        val startY = 500
        val endX = 540
        val endY = 1500
        val screenWidth = 1080
        val screenHeight = 2400

        // When
        val path = generator.generatePath(startX, startY, endX, endY, screenWidth, screenHeight)

        // Then
        assertTrue(path.points.size >= 2)
        assertEquals(startX, path.points.first().x)
        assertEquals(startY, path.points.first().y)
        assertEquals(endX, path.points.last().x)
        assertEquals(endY, path.points.last().y)
    }

    @Test
    fun `generatePath_verticalSwipeUp_generatesValidPath`() {
        // Given - vertical swipe up
        val startX = 540
        val startY = 1500
        val endX = 540
        val endY = 500
        val screenWidth = 1080
        val screenHeight = 2400

        // When
        val path = generator.generatePath(startX, startY, endX, endY, screenWidth, screenHeight)

        // Then
        assertTrue(path.points.size >= 2)
        assertEquals(startX, path.points.first().x)
        assertEquals(startY, path.points.first().y)
        assertEquals(endX, path.points.last().x)
        assertEquals(endY, path.points.last().y)
    }

    @Test
    fun `generatePath_horizontalSwipeRight_generatesValidPath`() {
        // Given - horizontal swipe right
        val startX = 100
        val startY = 1200
        val endX = 900
        val endY = 1200
        val screenWidth = 1080
        val screenHeight = 2400

        // When
        val path = generator.generatePath(startX, startY, endX, endY, screenWidth, screenHeight)

        // Then
        assertTrue(path.points.size >= 2)
        assertEquals(startX, path.points.first().x)
        assertEquals(startY, path.points.first().y)
        assertEquals(endX, path.points.last().x)
        assertEquals(endY, path.points.last().y)
    }

    @Test
    fun `generatePath_horizontalSwipeLeft_generatesValidPath`() {
        // Given - horizontal swipe left
        val startX = 900
        val startY = 1200
        val endX = 100
        val endY = 1200
        val screenWidth = 1080
        val screenHeight = 2400

        // When
        val path = generator.generatePath(startX, startY, endX, endY, screenWidth, screenHeight)

        // Then
        assertTrue(path.points.size >= 2)
        assertEquals(startX, path.points.first().x)
        assertEquals(startY, path.points.first().y)
        assertEquals(endX, path.points.last().x)
        assertEquals(endY, path.points.last().y)
    }

    @Test
    fun `generatePath_diagonalSwipe_generatesValidPath`() {
        // Given - diagonal swipe
        val startX = 100
        val startY = 500
        val endX = 900
        val endY = 1800
        val screenWidth = 1080
        val screenHeight = 2400

        // When
        val path = generator.generatePath(startX, startY, endX, endY, screenWidth, screenHeight)

        // Then
        assertTrue(path.points.size >= 2)
        assertEquals(startX, path.points.first().x)
        assertEquals(startY, path.points.first().y)
        assertEquals(endX, path.points.last().x)
        assertEquals(endY, path.points.last().y)
    }

    // ==================== Edge Case Tests ====================

    @Test
    fun `generatePath_sameStartAndEnd_generatesValidPath`() {
        // Given - same start and end (zero distance)
        val startX = 540
        val startY = 1200
        val endX = 540
        val endY = 1200
        val screenWidth = 1080
        val screenHeight = 2400

        // When
        val path = generator.generatePath(startX, startY, endX, endY, screenWidth, screenHeight)

        // Then - should still generate a valid path
        assertTrue(path.points.size >= 2)
        assertEquals(startX, path.points.first().x)
        assertEquals(startY, path.points.first().y)
        assertEquals(endX, path.points.last().x)
        assertEquals(endY, path.points.last().y)
    }

    @Test
    fun `generateLinearPath_sameStartAndEnd_generatesValidPath`() {
        // Given - same start and end (zero distance)
        val startX = 540
        val startY = 1200
        val endX = 540
        val endY = 1200
        val screenWidth = 1080
        val screenHeight = 2400

        // When
        val path = generator.generateLinearPath(startX, startY, endX, endY, screenWidth, screenHeight)

        // Then - should still generate a valid path
        assertTrue(path.points.size >= 2)
        assertEquals(startX, path.points.first().x)
        assertEquals(startY, path.points.first().y)
        assertEquals(endX, path.points.last().x)
        assertEquals(endY, path.points.last().y)
    }
}
