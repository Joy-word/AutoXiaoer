package com.flowmate.autoxiaoer.action

import com.flowmate.autoxiaoer.util.Logger
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Unit tests for [ActionParser].
 *
 * Tests parsing of various action types including Tap, Swipe, Type, Launch, and Finish.
 * Also tests error handling for invalid formats and out-of-range coordinates.
 *
 * _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8_
 */
class ActionParserTest {
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

    // ==================== Tap Action Tests ====================

    @Test
    fun `parse_validTapAction_returnsTapWithCorrectCoordinates`() {
        // Given
        val input = """do(action="Tap", element=[100, 200])"""

        // When
        val result = ActionParser.parse(input)

        // Then
        assertEquals(AgentAction.Tap(x = 100, y = 200, message = null), result)
    }

    @Test
    fun `parse_tapActionWithMessage_returnsTapWithMessage`() {
        // Given
        val input = """do(action="Tap", element=[500, 600], message="Confirm payment")"""

        // When
        val result = ActionParser.parse(input)

        // Then
        assertEquals(AgentAction.Tap(x = 500, y = 600, message = "Confirm payment"), result)
    }

    @Test
    fun `parse_tapActionWithBoundaryCoordinates_returnsTapWithCorrectCoordinates`() {
        // Given - test boundary values 0 and 999
        val inputMin = """do(action="Tap", element=[0, 0])"""
        val inputMax = """do(action="Tap", element=[999, 999])"""

        // When
        val resultMin = ActionParser.parse(inputMin)
        val resultMax = ActionParser.parse(inputMax)

        // Then
        assertEquals(AgentAction.Tap(x = 0, y = 0, message = null), resultMin)
        assertEquals(AgentAction.Tap(x = 999, y = 999, message = null), resultMax)
    }

    @Test
    fun `parse_tapActionWithSingleQuotes_returnsTapWithCorrectCoordinates`() {
        // Given
        val input = """do(action='Tap', element=[300, 400])"""

        // When
        val result = ActionParser.parse(input)

        // Then
        assertEquals(AgentAction.Tap(x = 300, y = 400, message = null), result)
    }

    // ==================== Swipe Action Tests ====================

    @Test
    fun `parse_validSwipeAction_returnsSwipeWithCorrectCoordinates`() {
        // Given
        val input = """do(action="Swipe", start=[100, 200], end=[300, 400])"""

        // When
        val result = ActionParser.parse(input)

        // Then
        assertEquals(
            AgentAction.Swipe(startX = 100, startY = 200, endX = 300, endY = 400),
            result,
        )
    }

    @Test
    fun `parse_swipeActionWithBoundaryCoordinates_returnsSwipeWithCorrectCoordinates`() {
        // Given
        val input = """do(action="Swipe", start=[0, 0], end=[999, 999])"""

        // When
        val result = ActionParser.parse(input)

        // Then
        assertEquals(
            AgentAction.Swipe(startX = 0, startY = 0, endX = 999, endY = 999),
            result,
        )
    }

    // ==================== Type Action Tests ====================

    @Test
    fun `parse_validTypeAction_returnsTypeWithCorrectText`() {
        // Given
        val input = """do(action="Type", text="Hello World")"""

        // When
        val result = ActionParser.parse(input)

        // Then
        assertEquals(AgentAction.Type(text = "Hello World"), result)
    }

    @Test
    fun `parse_typeActionWithEscapedQuotes_returnsTypeWithUnescapedText`() {
        // Given
        val input = """do(action="Type", text="He said \"Hello\"")"""

        // When
        val result = ActionParser.parse(input)

        // Then
        assertEquals(AgentAction.Type(text = """He said "Hello""""), result)
    }

    @Test
    fun `parse_typeActionWithSingleQuotes_returnsTypeWithCorrectText`() {
        // Given
        val input = """do(action='Type', text='Hello World')"""

        // When
        val result = ActionParser.parse(input)

        // Then
        assertEquals(AgentAction.Type(text = "Hello World"), result)
    }

    @Test
    fun `parse_typeActionWithEmptyText_returnsTypeWithEmptyString`() {
        // Given
        val input = """do(action="Type", text="")"""

        // When
        val result = ActionParser.parse(input)

        // Then
        assertEquals(AgentAction.Type(text = ""), result)
    }

    @Test
    fun `parse_typeActionWithSpecialCharacters_returnsTypeWithCorrectText`() {
        // Given
        val input = """do(action="Type", text="Test@123!#$%")"""

        // When
        val result = ActionParser.parse(input)

        // Then
        assertEquals(AgentAction.Type(text = "Test@123!#\$%"), result)
    }

    // ==================== Launch Action Tests ====================

    @Test
    fun `parse_validLaunchAction_returnsLaunchWithCorrectAppName`() {
        // Given
        val input = """do(action="Launch", app="WeChat")"""

        // When
        val result = ActionParser.parse(input)

        // Then
        assertEquals(AgentAction.Launch(app = "WeChat"), result)
    }

    @Test
    fun `parse_launchActionWithPackageName_returnsLaunchWithCorrectPackage`() {
        // Given
        val input = """do(action="Launch", app="com.tencent.mm")"""

        // When
        val result = ActionParser.parse(input)

        // Then
        assertEquals(AgentAction.Launch(app = "com.tencent.mm"), result)
    }

    // ==================== Finish Action Tests ====================

    @Test
    fun `parse_validFinishAction_returnsFinishWithCorrectMessage`() {
        // Given
        val input = """finish(message="Task completed successfully")"""

        // When
        val result = ActionParser.parse(input)

        // Then
        assertEquals(AgentAction.Finish(message = "Task completed successfully"), result)
    }

    @Test
    fun `parse_finishActionWithSingleQuotes_returnsFinishWithCorrectMessage`() {
        // Given
        val input = """finish(message='Task done')"""

        // When
        val result = ActionParser.parse(input)

        // Then
        assertEquals(AgentAction.Finish(message = "Task done"), result)
    }

    @Test
    fun `parse_finishActionWithEmptyMessage_returnsFinishWithEmptyString`() {
        // Given
        val input = """finish(message="")"""

        // When
        val result = ActionParser.parse(input)

        // Then
        assertEquals(AgentAction.Finish(message = ""), result)
    }

    @Test
    fun `parse_finishActionWithLongMessage_returnsFinishWithFullMessage`() {
        // Given
        val longMessage = "A".repeat(500)
        val input = """finish(message="$longMessage")"""

        // When
        val result = ActionParser.parse(input)

        // Then
        assertEquals(AgentAction.Finish(message = longMessage), result)
    }

    // ==================== Exception Tests ====================
    // _Requirements: 1.6, 1.7_

    @Test
    fun `parse_coordinateOutOfRange_throwsCoordinateOutOfRangeException`() {
        // Given - x coordinate > 999
        val input = """do(action="Tap", element=[1500, 500])"""

        // When/Then
        val exception =
            assertThrows<CoordinateOutOfRangeException> {
                ActionParser.parse(input)
            }

        // Verify exception contains the invalid coordinate
        assertTrue(exception.invalidCoordinates.any { it.name == "x" && it.value == 1500 })
    }

    @Test
    fun `parse_negativeCoordinate_throwsCoordinateOutOfRangeException`() {
        // Given - negative x coordinate
        val input = """do(action="Tap", element=[-10, 500])"""

        // When/Then
        val exception =
            assertThrows<CoordinateOutOfRangeException> {
                ActionParser.parse(input)
            }

        // Verify exception contains the invalid coordinate
        assertTrue(exception.invalidCoordinates.any { it.name == "x" && it.value == -10 })
    }

    @Test
    fun `parse_bothCoordinatesOutOfRange_throwsExceptionWithBothCoordinates`() {
        // Given - both coordinates out of range
        val input = """do(action="Tap", element=[-5, 1200])"""

        // When/Then
        val exception =
            assertThrows<CoordinateOutOfRangeException> {
                ActionParser.parse(input)
            }

        // Verify exception contains both invalid coordinates
        assertEquals(2, exception.invalidCoordinates.size)
        assertTrue(exception.invalidCoordinates.any { it.name == "x" && it.value == -5 })
        assertTrue(exception.invalidCoordinates.any { it.name == "y" && it.value == 1200 })
    }

    @Test
    fun `parse_swipeWithOutOfRangeCoordinates_throwsCoordinateOutOfRangeException`() {
        // Given - swipe with out of range end coordinates
        val input = """do(action="Swipe", start=[100, 200], end=[1500, 2000])"""

        // When/Then
        val exception =
            assertThrows<CoordinateOutOfRangeException> {
                ActionParser.parse(input)
            }

        // Verify exception contains the invalid coordinates
        assertTrue(exception.invalidCoordinates.any { it.name == "endX" && it.value == 1500 })
        assertTrue(exception.invalidCoordinates.any { it.name == "endY" && it.value == 2000 })
    }

    @Test
    fun `parse_veryLargeCoordinate_throwsCoordinateOutOfRangeException`() {
        // Given - very large coordinate value
        val input = """do(action="Tap", element=[999999, 500])"""

        // When/Then
        val exception =
            assertThrows<CoordinateOutOfRangeException> {
                ActionParser.parse(input)
            }

        assertTrue(exception.invalidCoordinates.any { it.name == "x" && it.value == 999999 })
    }

    @Test
    fun `parse_invalidActionFormat_throwsActionParseException`() {
        // Given - invalid format
        val input = "invalid action format"

        // When/Then
        assertThrows<ActionParseException> {
            ActionParser.parse(input)
        }
    }

    @Test
    fun `parse_unknownActionType_throwsActionParseException`() {
        // Given - unknown action type
        val input = """do(action="UnknownAction", element=[100, 200])"""

        // When/Then
        assertThrows<ActionParseException> {
            ActionParser.parse(input)
        }
    }

    @Test
    fun `parse_tapWithoutElement_throwsActionParseException`() {
        // Given - Tap action without element coordinates
        val input = """do(action="Tap")"""

        // When/Then
        assertThrows<ActionParseException> {
            ActionParser.parse(input)
        }
    }

    @Test
    fun `parse_swipeWithoutStart_throwsActionParseException`() {
        // Given - Swipe action without start coordinates
        val input = """do(action="Swipe", end=[300, 400])"""

        // When/Then
        assertThrows<ActionParseException> {
            ActionParser.parse(input)
        }
    }

    @Test
    fun `parse_swipeWithoutEnd_throwsActionParseException`() {
        // Given - Swipe action without end coordinates
        val input = """do(action="Swipe", start=[100, 200])"""

        // When/Then
        assertThrows<ActionParseException> {
            ActionParser.parse(input)
        }
    }

    @Test
    fun `parse_launchWithoutApp_throwsActionParseException`() {
        // Given - Launch action without app name
        val input = """do(action="Launch")"""

        // When/Then
        assertThrows<ActionParseException> {
            ActionParser.parse(input)
        }
    }

    // ==================== Batch Action Tests ====================
    // _Requirements: 1.9_

    @Test
    fun `parse_batchActionWithMultipleSteps_returnsBatchWithAllSteps`() {
        // Given - Batch action with multiple Tap steps
        val input =
            """do(action="Batch", steps=[""" +
                """{"action": "Tap", "element": [100, 200]}, """ +
                """{"action": "Tap", "element": [300, 400]}], delay=500)"""

        // When
        val result = ActionParser.parse(input)

        // Then
        assertTrue(result is AgentAction.Batch)
        val batch = result as AgentAction.Batch
        assertEquals(2, batch.steps.size)
        assertEquals(500, batch.delayMs)

        // Verify first step
        assertTrue(batch.steps[0] is AgentAction.Tap)
        val tap1 = batch.steps[0] as AgentAction.Tap
        assertEquals(100, tap1.x)
        assertEquals(200, tap1.y)

        // Verify second step
        assertTrue(batch.steps[1] is AgentAction.Tap)
        val tap2 = batch.steps[1] as AgentAction.Tap
        assertEquals(300, tap2.x)
        assertEquals(400, tap2.y)
    }

    @Test
    fun `parse_batchActionWithMixedSteps_returnsBatchWithAllSteps`() {
        // Given - Batch action with Tap and Swipe steps
        val input =
            """do(action="Batch", steps=[""" +
                """{"action": "Tap", "element": [100, 200]}, """ +
                """{"action": "Swipe", "start": [100, 200], "end": [300, 400]}], delay=300)"""

        // When
        val result = ActionParser.parse(input)

        // Then
        assertTrue(result is AgentAction.Batch)
        val batch = result as AgentAction.Batch
        assertEquals(2, batch.steps.size)
        assertEquals(300, batch.delayMs)

        // Verify first step is Tap
        assertTrue(batch.steps[0] is AgentAction.Tap)

        // Verify second step is Swipe
        assertTrue(batch.steps[1] is AgentAction.Swipe)
        val swipe = batch.steps[1] as AgentAction.Swipe
        assertEquals(100, swipe.startX)
        assertEquals(200, swipe.startY)
        assertEquals(300, swipe.endX)
        assertEquals(400, swipe.endY)
    }

    @Test
    fun `parse_batchActionWithDefaultDelay_returnsBatchWithDefaultDelay`() {
        // Given - Batch action without explicit delay
        val input = """do(action="Batch", steps=[{"action": "Tap", "element": [100, 200]}])"""

        // When
        val result = ActionParser.parse(input)

        // Then
        assertTrue(result is AgentAction.Batch)
        val batch = result as AgentAction.Batch
        assertEquals(500, batch.delayMs) // Default delay is 500ms
    }

    @Test
    fun `parse_batchActionWithEmptySteps_throwsActionParseException`() {
        // Given - Batch action with empty steps array
        val input = """do(action="Batch", steps=[], delay=500)"""

        // When/Then
        assertThrows<ActionParseException> {
            ActionParser.parse(input)
        }
    }

    @Test
    fun `parse_batchActionWithoutSteps_throwsActionParseException`() {
        // Given - Batch action without steps parameter
        val input = """do(action="Batch", delay=500)"""

        // When/Then
        assertThrows<ActionParseException> {
            ActionParser.parse(input)
        }
    }

    @Test
    fun `parse_batchActionWithWaitStep_returnsBatchWithWaitStep`() {
        // Given - Batch action with Wait step
        val input =
            """do(action="Batch", steps=[""" +
                """{"action": "Tap", "element": [100, 200]}, """ +
                """{"action": "Wait", "duration": "2 seconds"}])"""

        // When
        val result = ActionParser.parse(input)

        // Then
        assertTrue(result is AgentAction.Batch)
        val batch = result as AgentAction.Batch
        assertEquals(2, batch.steps.size)

        // Verify Wait step
        assertTrue(batch.steps[1] is AgentAction.Wait)
        val wait = batch.steps[1] as AgentAction.Wait
        assertEquals(2.0f, wait.durationSeconds)
    }
}

// ==================== Long Press Action Tests ====================

@Test
fun `parse_validLongPressAction_returnsLongPressWithCorrectCoordinates`() {
    // Given
    val input = """do(action="Long Press", element=[500, 600])"""

    // When
    val result = ActionParser.parse(input)

    // Then
    assertTrue(result is AgentAction.LongPress)
    val longPress = result as AgentAction.LongPress
    assertEquals(500, longPress.x)
    assertEquals(600, longPress.y)
    assertEquals(3000, longPress.durationMs) // Default duration
}

@Test
fun `parse_longPressActionWithDuration_returnsLongPressWithCustomDuration`() {
    // Given
    val input = """do(action="Long Press", element=[500, 600], duration=5000)"""

    // When
    val result = ActionParser.parse(input)

    // Then
    assertTrue(result is AgentAction.LongPress)
    val longPress = result as AgentAction.LongPress
    assertEquals(500, longPress.x)
    assertEquals(600, longPress.y)
    assertEquals(5000, longPress.durationMs)
}

@Test
fun `parse_longPressActionWithBoundaryCoordinates_returnsLongPressWithCorrectCoordinates`() {
    // Given
    val input = """do(action="Long Press", element=[0, 999])"""

    // When
    val result = ActionParser.parse(input)

    // Then
    assertTrue(result is AgentAction.LongPress)
    val longPress = result as AgentAction.LongPress
    assertEquals(0, longPress.x)
    assertEquals(999, longPress.y)
}

@Test
fun `parse_longPressActionWithOutOfRangeCoordinates_throwsCoordinateOutOfRangeException`() {
    // Given
    val input = """do(action="Long Press", element=[1500, 600])"""

    // When/Then
    val exception =
        assertThrows<CoordinateOutOfRangeException> {
            ActionParser.parse(input)
        }
    assertTrue(exception.invalidCoordinates.any { it.name == "x" && it.value == 1500 })
}

// ==================== Double Tap Action Tests ====================

@Test
fun `parse_validDoubleTapAction_returnsDoubleTapWithCorrectCoordinates`() {
    // Given
    val input = """do(action="Double Tap", element=[300, 400])"""

    // When
    val result = ActionParser.parse(input)

    // Then
    assertTrue(result is AgentAction.DoubleTap)
    val doubleTap = result as AgentAction.DoubleTap
    assertEquals(300, doubleTap.x)
    assertEquals(400, doubleTap.y)
}

@Test
fun `parse_doubleTapActionWithBoundaryCoordinates_returnsDoubleTapWithCorrectCoordinates`() {
    // Given
    val input = """do(action="Double Tap", element=[999, 0])"""

    // When
    val result = ActionParser.parse(input)

    // Then
    assertTrue(result is AgentAction.DoubleTap)
    val doubleTap = result as AgentAction.DoubleTap
    assertEquals(999, doubleTap.x)
    assertEquals(0, doubleTap.y)
}

@Test
fun `parse_doubleTapActionWithOutOfRangeCoordinates_throwsCoordinateOutOfRangeException`() {
    // Given
    val input = """do(action="Double Tap", element=[-10, 400])"""

    // When/Then
    val exception =
        assertThrows<CoordinateOutOfRangeException> {
            ActionParser.parse(input)
        }
    assertTrue(exception.invalidCoordinates.any { it.name == "x" && it.value == -10 })
}

// ==================== System Key Action Tests ====================

@Test
fun `parse_backAction_returnsBackAction`() {
    // Given
    val input = """do(action="Back")"""

    // When
    val result = ActionParser.parse(input)

    // Then
    assertEquals(AgentAction.Back, result)
}

@Test
fun `parse_homeAction_returnsHomeAction`() {
    // Given
    val input = """do(action="Home")"""

    // When
    val result = ActionParser.parse(input)

    // Then
    assertEquals(AgentAction.Home, result)
}

@Test
fun `parse_volumeUpAction_returnsVolumeUpAction`() {
    // Given
    val input = """do(action="VolumeUp")"""

    // When
    val result = ActionParser.parse(input)

    // Then
    assertEquals(AgentAction.VolumeUp, result)
}

@Test
fun `parse_volumeDownAction_returnsVolumeDownAction`() {
    // Given
    val input = """do(action="VolumeDown")"""

    // When
    val result = ActionParser.parse(input)

    // Then
    assertEquals(AgentAction.VolumeDown, result)
}

@Test
fun `parse_powerAction_returnsPowerAction`() {
    // Given
    val input = """do(action="Power")"""

    // When
    val result = ActionParser.parse(input)

    // Then
    assertEquals(AgentAction.Power, result)
}

// ==================== List Apps Action Tests ====================

@Test
fun `parse_listAppsAction_returnsListAppsAction`() {
    // Given
    val input = """do(action="List_Apps")"""

    // When
    val result = ActionParser.parse(input)

    // Then
    assertEquals(AgentAction.ListApps, result)
}

@Test
fun `parse_listAppsActionAlternative_returnsListAppsAction`() {
    // Given - alternative format without underscore
    val input = """do(action="ListApps")"""

    // When
    val result = ActionParser.parse(input)

    // Then
    assertEquals(AgentAction.ListApps, result)
}

// ==================== Wait Action Tests ====================

@Test
fun `parse_waitActionWithSeconds_returnsWaitWithCorrectDuration`() {
    // Given
    val input = """do(action="Wait", duration="3 seconds")"""

    // When
    val result = ActionParser.parse(input)

    // Then
    assertTrue(result is AgentAction.Wait)
    val wait = result as AgentAction.Wait
    assertEquals(3.0f, wait.durationSeconds)
}

@Test
fun `parse_waitActionWithDecimalDuration_returnsWaitWithCorrectDuration`() {
    // Given
    val input = """do(action="Wait", duration="2.5")"""

    // When
    val result = ActionParser.parse(input)

    // Then
    assertTrue(result is AgentAction.Wait)
    val wait = result as AgentAction.Wait
    assertEquals(2.5f, wait.durationSeconds)
}

@Test
fun `parse_waitActionWithoutDuration_returnsWaitWithDefaultDuration`() {
    // Given
    val input = """do(action="Wait")"""

    // When
    val result = ActionParser.parse(input)

    // Then
    assertTrue(result is AgentAction.Wait)
    val wait = result as AgentAction.Wait
    assertEquals(1.0f, wait.durationSeconds) // Default is 1 second
}

// ==================== Take Over Action Tests ====================

@Test
fun `parse_takeOverAction_returnsTakeOverWithMessage`() {
    // Given
    val input = """do(action="Take_over", message="Please enter password")"""

    // When
    val result = ActionParser.parse(input)

    // Then
    assertTrue(result is AgentAction.TakeOver)
    val takeOver = result as AgentAction.TakeOver
    assertEquals("Please enter password", takeOver.message)
}

@Test
fun `parse_takeOverActionWithoutMessage_returnsTakeOverWithDefaultMessage`() {
    // Given
    val input = """do(action="Take_over")"""

    // When
    val result = ActionParser.parse(input)

    // Then
    assertTrue(result is AgentAction.TakeOver)
    val takeOver = result as AgentAction.TakeOver
    assertEquals("User intervention required", takeOver.message)
}

// ==================== Interact Action Tests ====================

@Test
fun `parse_interactAction_returnsInteractAction`() {
    // Given
    val input = """do(action="Interact")"""

    // When
    val result = ActionParser.parse(input)

    // Then
    assertTrue(result is AgentAction.Interact)
}

// ==================== Note Action Tests ====================

@Test
fun `parse_noteAction_returnsNoteWithMessage`() {
    // Given
    val input = """do(action="Note", message="Remember to check settings")"""

    // When
    val result = ActionParser.parse(input)

    // Then
    assertTrue(result is AgentAction.Note)
    val note = result as AgentAction.Note
    assertEquals("Remember to check settings", note.message)
}

@Test
fun `parse_noteActionWithoutMessage_returnsNoteWithEmptyMessage`() {
    // Given
    val input = """do(action="Note")"""

    // When
    val result = ActionParser.parse(input)

    // Then
    assertTrue(result is AgentAction.Note)
    val note = result as AgentAction.Note
    assertEquals("", note.message)
}

// ==================== Call API Action Tests ====================

@Test
fun `parse_callApiAction_returnsCallApiWithInstruction`() {
    // Given
    val input = """do(action="Call_API", instruction="Get weather data")"""

    // When
    val result = ActionParser.parse(input)

    // Then
    assertTrue(result is AgentAction.CallApi)
    val callApi = result as AgentAction.CallApi
    assertEquals("Get weather data", callApi.instruction)
}

@Test
fun `parse_callApiActionWithoutInstruction_returnsCallApiWithEmptyInstruction`() {
    // Given
    val input = """do(action="Call_API")"""

    // When
    val result = ActionParser.parse(input)

    // Then
    assertTrue(result is AgentAction.CallApi)
    val callApi = result as AgentAction.CallApi
    assertEquals("", callApi.instruction)
}

// ==================== Type Name Action Tests ====================

@Test
fun `parse_typeNameAction_returnsTypeNameWithCorrectText`() {
    // Given
    val input = """do(action="Type_Name", text="John Doe")"""

    // When
    val result = ActionParser.parse(input)

    // Then
    assertTrue(result is AgentAction.TypeName)
    val typeName = result as AgentAction.TypeName
    assertEquals("John Doe", typeName.text)
}

@Test
fun `parse_typeNameActionWithEmptyText_returnsTypeNameWithEmptyString`() {
    // Given
    val input = """do(action="Type_Name", text="")"""

    // When
    val result = ActionParser.parse(input)

    // Then
    assertTrue(result is AgentAction.TypeName)
    val typeName = result as AgentAction.TypeName
    assertEquals("", typeName.text)
}

// ==================== Batch Action with System Keys Tests ====================

@Test
fun `parse_batchActionWithBackStep_returnsBatchWithBackStep`() {
    // Given
    val input =
        """do(action="Batch", steps=[""" +
            """{"action": "Tap", "element": [100, 200]}, """ +
            """{"action": "Back"}])"""

    // When
    val result = ActionParser.parse(input)

    // Then
    assertTrue(result is AgentAction.Batch)
    val batch = result as AgentAction.Batch
    assertEquals(2, batch.steps.size)
    assertTrue(batch.steps[0] is AgentAction.Tap)
    assertEquals(AgentAction.Back, batch.steps[1])
}

@Test
fun `parse_batchActionWithHomeStep_returnsBatchWithHomeStep`() {
    // Given
    val input =
        """do(action="Batch", steps=[""" +
            """{"action": "Home"}])"""

    // When
    val result = ActionParser.parse(input)

    // Then
    assertTrue(result is AgentAction.Batch)
    val batch = result as AgentAction.Batch
    assertEquals(1, batch.steps.size)
    assertEquals(AgentAction.Home, batch.steps[0])
}

@Test
fun `parse_batchActionWithLongPressStep_returnsBatchWithLongPressStep`() {
    // Given
    val input =
        """do(action="Batch", steps=[""" +
            """{"action": "Long Press", "element": [500, 500]}])"""

    // When
    val result = ActionParser.parse(input)

    // Then
    assertTrue(result is AgentAction.Batch)
    val batch = result as AgentAction.Batch
    assertEquals(1, batch.steps.size)
    assertTrue(batch.steps[0] is AgentAction.LongPress)
    val longPress = batch.steps[0] as AgentAction.LongPress
    assertEquals(500, longPress.x)
    assertEquals(500, longPress.y)
}

@Test
fun `parse_batchActionWithDoubleTapStep_returnsBatchWithDoubleTapStep`() {
    // Given
    val input =
        """do(action="Batch", steps=[""" +
            """{"action": "Double Tap", "element": [300, 400]}])"""

    // When
    val result = ActionParser.parse(input)

    // Then
    assertTrue(result is AgentAction.Batch)
    val batch = result as AgentAction.Batch
    assertEquals(1, batch.steps.size)
    assertTrue(batch.steps[0] is AgentAction.DoubleTap)
    val doubleTap = batch.steps[0] as AgentAction.DoubleTap
    assertEquals(300, doubleTap.x)
    assertEquals(400, doubleTap.y)
}
