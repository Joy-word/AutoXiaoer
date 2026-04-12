package com.flowmate.autoxiaoer.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Unit tests for [CoordinateConverter].
 *
 * Tests coordinate conversion between relative (0-999) and absolute screen coordinates.
 *
 * _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6_
 */
class CoordinateConverterTest {
    // ==================== toAbsoluteX Tests ====================
    // _Requirements: 2.1_

    @Test
    fun `toAbsoluteX_relativeZero_returnsZero`() {
        // Given
        val relativeX = 0
        val screenWidth = 1080

        // When
        val result = CoordinateConverter.toAbsoluteX(relativeX, screenWidth)

        // Then
        assertEquals(0, result)
    }

    @Test
    fun `toAbsoluteX_relativeMax_returnsCloseToScreenWidth`() {
        // Given
        val relativeX = 999
        val screenWidth = 1080

        // When
        val result = CoordinateConverter.toAbsoluteX(relativeX, screenWidth)

        // Then - 999 * 1080 / 1000 = 1078.92 -> 1078
        assertEquals(1078, result)
    }

    @Test
    fun `toAbsoluteX_relativeMiddle_returnsMiddleOfScreen`() {
        // Given
        val relativeX = 500
        val screenWidth = 1080

        // When
        val result = CoordinateConverter.toAbsoluteX(relativeX, screenWidth)

        // Then - 500 * 1080 / 1000 = 540
        assertEquals(540, result)
    }

    @Test
    fun `toAbsoluteX_differentScreenWidth_scalesProportionally`() {
        // Given
        val relativeX = 500
        val screenWidth = 2160

        // When
        val result = CoordinateConverter.toAbsoluteX(relativeX, screenWidth)

        // Then - 500 * 2160 / 1000 = 1080
        assertEquals(1080, result)
    }

    // ==================== toAbsoluteY Tests ====================
    // _Requirements: 2.2_

    @Test
    fun `toAbsoluteY_relativeZero_returnsZero`() {
        // Given
        val relativeY = 0
        val screenHeight = 2400

        // When
        val result = CoordinateConverter.toAbsoluteY(relativeY, screenHeight)

        // Then
        assertEquals(0, result)
    }

    @Test
    fun `toAbsoluteY_relativeMax_returnsCloseToScreenHeight`() {
        // Given
        val relativeY = 999
        val screenHeight = 2400

        // When
        val result = CoordinateConverter.toAbsoluteY(relativeY, screenHeight)

        // Then - 999 * 2400 / 1000 = 2397.6 -> 2397
        assertEquals(2397, result)
    }

    @Test
    fun `toAbsoluteY_relativeMiddle_returnsMiddleOfScreen`() {
        // Given
        val relativeY = 500
        val screenHeight = 2400

        // When
        val result = CoordinateConverter.toAbsoluteY(relativeY, screenHeight)

        // Then - 500 * 2400 / 1000 = 1200
        assertEquals(1200, result)
    }

    // ==================== toRelativeX Tests ====================
    // _Requirements: 2.3_

    @Test
    fun `toRelativeX_absoluteZero_returnsZero`() {
        // Given
        val absoluteX = 0
        val screenWidth = 1080

        // When
        val result = CoordinateConverter.toRelativeX(absoluteX, screenWidth)

        // Then
        assertEquals(0, result)
    }

    @Test
    fun `toRelativeX_absoluteMax_returnsCloseToMax`() {
        // Given
        val absoluteX = 1079
        val screenWidth = 1080

        // When
        val result = CoordinateConverter.toRelativeX(absoluteX, screenWidth)

        // Then - 1079 * 1000 / 1080 = 999.07 -> 999
        assertEquals(999, result)
    }

    @Test
    fun `toRelativeX_absoluteMiddle_returnsMiddle`() {
        // Given
        val absoluteX = 540
        val screenWidth = 1080

        // When
        val result = CoordinateConverter.toRelativeX(absoluteX, screenWidth)

        // Then - 540 * 1000 / 1080 = 500
        assertEquals(500, result)
    }

    // ==================== toRelativeY Tests ====================
    // _Requirements: 2.4_

    @Test
    fun `toRelativeY_absoluteZero_returnsZero`() {
        // Given
        val absoluteY = 0
        val screenHeight = 2400

        // When
        val result = CoordinateConverter.toRelativeY(absoluteY, screenHeight)

        // Then
        assertEquals(0, result)
    }

    @Test
    fun `toRelativeY_absoluteMax_returnsCloseToMax`() {
        // Given
        val absoluteY = 2399
        val screenHeight = 2400

        // When
        val result = CoordinateConverter.toRelativeY(absoluteY, screenHeight)

        // Then - 2399 * 1000 / 2400 = 999.58 -> 999
        assertEquals(999, result)
    }

    @Test
    fun `toRelativeY_absoluteMiddle_returnsMiddle`() {
        // Given
        val absoluteY = 1200
        val screenHeight = 2400

        // When
        val result = CoordinateConverter.toRelativeY(absoluteY, screenHeight)

        // Then - 1200 * 1000 / 2400 = 500
        assertEquals(500, result)
    }

    // ==================== toAbsolute (Pair) Tests ====================

    @Test
    fun `toAbsolute_validCoordinates_returnsPairWithCorrectValues`() {
        // Given
        val relativeX = 500
        val relativeY = 500
        val screenWidth = 1080
        val screenHeight = 2400

        // When
        val result = CoordinateConverter.toAbsolute(relativeX, relativeY, screenWidth, screenHeight)

        // Then
        assertEquals(Pair(540, 1200), result)
    }

    @Test
    fun `toAbsolute_boundaryZero_returnsPairWithZeros`() {
        // Given
        val relativeX = 0
        val relativeY = 0
        val screenWidth = 1080
        val screenHeight = 2400

        // When
        val result = CoordinateConverter.toAbsolute(relativeX, relativeY, screenWidth, screenHeight)

        // Then
        assertEquals(Pair(0, 0), result)
    }

    @Test
    fun `toAbsolute_boundaryMax_returnsPairCloseToScreenDimensions`() {
        // Given
        val relativeX = 999
        val relativeY = 999
        val screenWidth = 1080
        val screenHeight = 2400

        // When
        val result = CoordinateConverter.toAbsolute(relativeX, relativeY, screenWidth, screenHeight)

        // Then - 999 * 1080 / 1000 = 1078, 999 * 2400 / 1000 = 2397
        assertEquals(Pair(1078, 2397), result)
    }

    // ==================== Different Screen Sizes Tests ====================
    // _Requirements: 2.5, 2.6_

    @Test
    fun `toAbsoluteX_smallScreen_scalesCorrectly`() {
        // Given - small screen (e.g., 720p)
        val relativeX = 500
        val screenWidth = 720

        // When
        val result = CoordinateConverter.toAbsoluteX(relativeX, screenWidth)

        // Then - 500 * 720 / 1000 = 360
        assertEquals(360, result)
    }

    @Test
    fun `toAbsoluteY_smallScreen_scalesCorrectly`() {
        // Given - small screen (e.g., 720p)
        val relativeY = 500
        val screenHeight = 1280

        // When
        val result = CoordinateConverter.toAbsoluteY(relativeY, screenHeight)

        // Then - 500 * 1280 / 1000 = 640
        assertEquals(640, result)
    }

    @Test
    fun `toAbsoluteX_largeScreen_scalesCorrectly`() {
        // Given - large screen (e.g., 4K)
        val relativeX = 500
        val screenWidth = 3840

        // When
        val result = CoordinateConverter.toAbsoluteX(relativeX, screenWidth)

        // Then - 500 * 3840 / 1000 = 1920
        assertEquals(1920, result)
    }

    @Test
    fun `toAbsoluteY_largeScreen_scalesCorrectly`() {
        // Given - large screen (e.g., 4K)
        val relativeY = 500
        val screenHeight = 2160

        // When
        val result = CoordinateConverter.toAbsoluteY(relativeY, screenHeight)

        // Then - 500 * 2160 / 1000 = 1080
        assertEquals(1080, result)
    }

    // ==================== Boundary Value Tests ====================
    // _Requirements: 2.5, 2.6_

    @Test
    fun `toAbsoluteX_boundaryZero_alwaysReturnsZero`() {
        // Given - boundary value 0 with various screen sizes
        val screenWidths = listOf(720, 1080, 1440, 2160, 3840)

        // When/Then
        screenWidths.forEach { screenWidth ->
            val result = CoordinateConverter.toAbsoluteX(0, screenWidth)
            assertEquals(0, result, "Failed for screenWidth=$screenWidth")
        }
    }

    @Test
    fun `toAbsoluteY_boundaryZero_alwaysReturnsZero`() {
        // Given - boundary value 0 with various screen sizes
        val screenHeights = listOf(1280, 1920, 2400, 2560, 4320)

        // When/Then
        screenHeights.forEach { screenHeight ->
            val result = CoordinateConverter.toAbsoluteY(0, screenHeight)
            assertEquals(0, result, "Failed for screenHeight=$screenHeight")
        }
    }

    @Test
    fun `toAbsoluteX_boundary999_returnsLessThanScreenWidth`() {
        // Given - boundary value 999 with various screen sizes
        val screenWidths = listOf(720, 1080, 1440, 2160, 3840)

        // When/Then
        screenWidths.forEach { screenWidth ->
            val result = CoordinateConverter.toAbsoluteX(999, screenWidth)
            // Result should be close to but less than screenWidth
            assert(result < screenWidth) { "Result $result should be less than screenWidth $screenWidth" }
            assert(result > screenWidth * 0.99) { "Result $result should be close to screenWidth $screenWidth" }
        }
    }

    @Test
    fun `toAbsoluteY_boundary999_returnsLessThanScreenHeight`() {
        // Given - boundary value 999 with various screen sizes
        val screenHeights = listOf(1280, 1920, 2400, 2560, 4320)

        // When/Then
        screenHeights.forEach { screenHeight ->
            val result = CoordinateConverter.toAbsoluteY(999, screenHeight)
            // Result should be close to but less than screenHeight
            assert(result < screenHeight) { "Result $result should be less than screenHeight $screenHeight" }
            assert(result > screenHeight * 0.99) { "Result $result should be close to screenHeight $screenHeight" }
        }
    }

    // ==================== RELATIVE_MAX Constant Test ====================

    @Test
    fun `relativeMax_isCorrectValue`() {
        // Verify the constant is set correctly
        assertEquals(1000, CoordinateConverter.RELATIVE_MAX)
    }
}
