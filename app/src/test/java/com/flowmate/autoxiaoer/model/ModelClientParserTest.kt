package com.flowmate.autoxiaoer.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for [ModelResponseParser].
 *
 * Tests parsing of model responses including thinking/action separation,
 * nested parentheses handling, and XML tag stripping.
 *
 * _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5_
 */
class ModelClientParserTest {
    // ==================== Thinking and Do Action Separation Tests ====================
    // _Requirements: 4.1_

    @Test
    fun `parseThinkingAndAction_thinkingAndDoAction_separatesCorrectly`() {
        // Given
        val content = """I need to tap on the button to proceed.
do(action="Tap", element=[500, 300])"""

        // When
        val (thinking, action) = ModelResponseParser.parseThinkingAndAction(content)

        // Then
        assertEquals("I need to tap on the button to proceed.", thinking)
        assertEquals("""do(action="Tap", element=[500, 300])""", action)
    }

    @Test
    fun `parseThinkingAndAction_multiLineThinking_separatesCorrectly`() {
        // Given
        val content = """First, I'll analyze the screen.
The user wants to send a message.
I should tap on the input field.
do(action="Tap", element=[100, 200])"""

        // When
        val (thinking, action) = ModelResponseParser.parseThinkingAndAction(content)

        // Then
        assertTrue(thinking.contains("First, I'll analyze the screen."))
        assertTrue(thinking.contains("I should tap on the input field."))
        assertEquals("""do(action="Tap", element=[100, 200])""", action)
    }

    @Test
    fun `parseThinkingAndAction_doActionWithSwipe_separatesCorrectly`() {
        // Given
        val content = """I need to scroll down to see more content.
do(action="Swipe", start=[500, 800], end=[500, 200])"""

        // When
        val (thinking, action) = ModelResponseParser.parseThinkingAndAction(content)

        // Then
        assertEquals("I need to scroll down to see more content.", thinking)
        assertEquals("""do(action="Swipe", start=[500, 800], end=[500, 200])""", action)
    }

    @Test
    fun `parseThinkingAndAction_doActionWithType_separatesCorrectly`() {
        // Given
        val content = """I'll type the message now.
do(action="Type", text="Hello World")"""

        // When
        val (thinking, action) = ModelResponseParser.parseThinkingAndAction(content)

        // Then
        assertEquals("I'll type the message now.", thinking)
        assertEquals("""do(action="Type", text="Hello World")""", action)
    }

    // ==================== Thinking and Finish Action Separation Tests ====================
    // _Requirements: 4.2_

    @Test
    fun `parseThinkingAndAction_thinkingAndFinishAction_separatesCorrectly`() {
        // Given
        val content = """The task has been completed successfully.
finish(message="Task completed")"""

        // When
        val (thinking, action) = ModelResponseParser.parseThinkingAndAction(content)

        // Then
        assertEquals("The task has been completed successfully.", thinking)
        assertEquals("""finish(message="Task completed")""", action)
    }

    @Test
    fun `parseThinkingAndAction_finishWithLongMessage_separatesCorrectly`() {
        // Given
        val content = """I have finished all the required steps.
finish(message="Successfully sent the message to the recipient and confirmed delivery")"""

        // When
        val (thinking, action) = ModelResponseParser.parseThinkingAndAction(content)

        // Then
        assertEquals("I have finished all the required steps.", thinking)
        assertEquals(
            """finish(message="Successfully sent the message to the recipient and confirmed delivery")""",
            action,
        )
    }

    @Test
    fun `parseThinkingAndAction_finishWithSingleQuotes_separatesCorrectly`() {
        // Given
        val content = """Done with the task.
finish(message='Task done')"""

        // When
        val (thinking, action) = ModelResponseParser.parseThinkingAndAction(content)

        // Then
        assertEquals("Done with the task.", thinking)
        assertEquals("""finish(message='Task done')""", action)
    }

    // ==================== Nested Parentheses Tests ====================
    // _Requirements: 4.3_

    @Test
    fun `parseThinkingAndAction_nestedParenthesesInText_handlesCorrectly`() {
        // Given - text contains parentheses
        val content = """I'll type a message with parentheses.
do(action="Type", text="Hello (world)")"""

        // When
        val (thinking, action) = ModelResponseParser.parseThinkingAndAction(content)

        // Then
        assertEquals("I'll type a message with parentheses.", thinking)
        assertEquals("""do(action="Type", text="Hello (world)")""", action)
    }

    @Test
    fun `parseThinkingAndAction_multipleNestedParentheses_handlesCorrectly`() {
        // Given - text contains multiple nested parentheses
        val content = """Typing complex text.
do(action="Type", text="func(a, b) returns (x, y)")"""

        // When
        val (thinking, action) = ModelResponseParser.parseThinkingAndAction(content)

        // Then
        assertEquals("Typing complex text.", thinking)
        assertEquals("""do(action="Type", text="func(a, b) returns (x, y)")""", action)
    }

    @Test
    fun `parseThinkingAndAction_deeplyNestedParentheses_handlesCorrectly`() {
        // Given - deeply nested parentheses
        val content = """Complex expression.
do(action="Type", text="((a + b) * (c + d))")"""

        // When
        val (thinking, action) = ModelResponseParser.parseThinkingAndAction(content)

        // Then
        assertEquals("Complex expression.", thinking)
        assertEquals("""do(action="Type", text="((a + b) * (c + d))")""", action)
    }

    @Test
    fun `parseThinkingAndAction_finishWithNestedParentheses_handlesCorrectly`() {
        // Given - finish message with parentheses
        val content = """Task complete.
finish(message="Done (see results)")"""

        // When
        val (thinking, action) = ModelResponseParser.parseThinkingAndAction(content)

        // Then
        assertEquals("Task complete.", thinking)
        assertEquals("""finish(message="Done (see results)")""", action)
    }

    // ==================== XML Tag Stripping Tests ====================
    // _Requirements: 4.4_

    @Test
    fun `parseThinkingAndAction_withThinkTags_stripsTagsCorrectly`() {
        // Given
        val content = """<think>I need to analyze this screen.</think>
do(action="Tap", element=[100, 200])"""

        // When
        val (thinking, action) = ModelResponseParser.parseThinkingAndAction(content)

        // Then
        assertEquals("I need to analyze this screen.", thinking)
        assertEquals("""do(action="Tap", element=[100, 200])""", action)
    }

    @Test
    fun `parseThinkingAndAction_withAnswerTags_stripsTagsCorrectly`() {
        // Given
        val content = """<think>Analyzing...</think>
<answer>do(action="Tap", element=[100, 200])</answer>"""

        // When
        val (thinking, action) = ModelResponseParser.parseThinkingAndAction(content)

        // Then
        assertEquals("Analyzing...", thinking)
        assertEquals("""do(action="Tap", element=[100, 200])""", action)
    }

    @Test
    fun `parseThinkingAndAction_withBothTags_stripsAllTagsCorrectly`() {
        // Given
        val content = """<think>
Let me think about this.
I should tap the button.
</think>
<answer>
do(action="Tap", element=[500, 600])
</answer>"""

        // When
        val (thinking, action) = ModelResponseParser.parseThinkingAndAction(content)

        // Then
        assertTrue(thinking.contains("Let me think about this."))
        assertTrue(thinking.contains("I should tap the button."))
        assertEquals("""do(action="Tap", element=[500, 600])""", action)
    }

    @Test
    fun `parseThinkingAndAction_withNestedWhitespaceInTags_stripsCorrectly`() {
        // Given
        val content = """<think>   Thinking with spaces   </think>
do(action="Tap", element=[100, 200])"""

        // When
        val (thinking, action) = ModelResponseParser.parseThinkingAndAction(content)

        // Then
        assertTrue(thinking.contains("Thinking with spaces"))
        assertEquals("""do(action="Tap", element=[100, 200])""", action)
    }

    // ==================== No Action Response Tests ====================
    // _Requirements: 4.5_

    @Test
    fun `parseThinkingAndAction_noAction_returnsEmptyAction`() {
        // Given
        val content = "I'm still analyzing the screen and need more information."

        // When
        val (thinking, action) = ModelResponseParser.parseThinkingAndAction(content)

        // Then
        assertEquals("I'm still analyzing the screen and need more information.", thinking)
        assertEquals("", action)
    }

    @Test
    fun `parseThinkingAndAction_emptyContent_returnsEmptyStrings`() {
        // Given
        val content = ""

        // When
        val (thinking, action) = ModelResponseParser.parseThinkingAndAction(content)

        // Then
        assertEquals("", thinking)
        assertEquals("", action)
    }

    @Test
    fun `parseThinkingAndAction_onlyWhitespace_returnsEmptyStrings`() {
        // Given
        val content = "   \n\t  "

        // When
        val (thinking, action) = ModelResponseParser.parseThinkingAndAction(content)

        // Then
        assertEquals("", thinking)
        assertEquals("", action)
    }

    @Test
    fun `parseThinkingAndAction_thinkingWithoutAction_returnsAllAsThinking`() {
        // Given
        val content = """<think>
I need to wait for the page to load.
The button is not visible yet.
</think>"""

        // When
        val (thinking, action) = ModelResponseParser.parseThinkingAndAction(content)

        // Then
        assertTrue(thinking.contains("I need to wait for the page to load."))
        assertTrue(thinking.contains("The button is not visible yet."))
        assertEquals("", action)
    }

    // ==================== Helper Method Tests ====================

    @Test
    fun `isDoAction_validDoAction_returnsTrue`() {
        // Given
        val action = """do(action="Tap", element=[100, 200])"""

        // When/Then
        assertTrue(ModelResponseParser.isDoAction(action))
    }

    @Test
    fun `isDoAction_doWithSpace_returnsTrue`() {
        // Given
        val action = """do (action="Tap", element=[100, 200])"""

        // When/Then
        assertTrue(ModelResponseParser.isDoAction(action))
    }

    @Test
    fun `isFinishAction_validFinishAction_returnsTrue`() {
        // Given
        val action = """finish(message="Done")"""

        // When/Then
        assertTrue(ModelResponseParser.isFinishAction(action))
    }

    @Test
    fun `isFinishAction_finishWithSpace_returnsTrue`() {
        // Given
        val action = """finish (message="Done")"""

        // When/Then
        assertTrue(ModelResponseParser.isFinishAction(action))
    }

    @Test
    fun `extractFinishMessage_validFinishAction_returnsMessage`() {
        // Given
        val action = """finish(message="Task completed successfully")"""

        // When
        val message = ModelResponseParser.extractFinishMessage(action)

        // Then
        assertEquals("Task completed successfully", message)
    }

    @Test
    fun `extractFinishMessage_singleQuotes_returnsMessage`() {
        // Given
        val action = """finish(message='Task done')"""

        // When
        val message = ModelResponseParser.extractFinishMessage(action)

        // Then
        assertEquals("Task done", message)
    }

    @Test
    fun `extractFinishMessage_escapedQuotes_returnsUnescapedMessage`() {
        // Given
        val action = """finish(message="He said \"Hello\"")"""

        // When
        val message = ModelResponseParser.extractFinishMessage(action)

        // Then
        assertEquals("""He said "Hello"""", message)
    }

    @Test
    fun `extractFinishMessage_notFinishAction_returnsNull`() {
        // Given
        val action = """do(action="Tap", element=[100, 200])"""

        // When
        val message = ModelResponseParser.extractFinishMessage(action)

        // Then
        assertNull(message)
    }

    @Test
    fun `extractFinishMessage_emptyMessage_returnsEmptyString`() {
        // Given
        val action = """finish(message="")"""

        // When
        val message = ModelResponseParser.extractFinishMessage(action)

        // Then
        assertEquals("", message)
    }

    // ==================== Edge Cases ====================

    @Test
    fun `parseThinkingAndAction_actionAtStart_returnsEmptyThinking`() {
        // Given
        val content = """do(action="Tap", element=[100, 200])"""

        // When
        val (thinking, action) = ModelResponseParser.parseThinkingAndAction(content)

        // Then
        assertEquals("", thinking)
        assertEquals("""do(action="Tap", element=[100, 200])""", action)
    }

    @Test
    fun `parseThinkingAndAction_multipleActions_returnsFirstAction`() {
        // Given - multiple actions (should return the first one)
        val content = """Thinking...
do(action="Tap", element=[100, 200])
do(action="Tap", element=[300, 400])"""

        // When
        val (thinking, action) = ModelResponseParser.parseThinkingAndAction(content)

        // Then
        assertEquals("Thinking...", thinking)
        assertEquals("""do(action="Tap", element=[100, 200])""", action)
    }

    @Test
    fun `parseThinkingAndAction_doBeforeFinish_returnsDoAction`() {
        // Given - do action appears before finish
        val content = """Thinking...
do(action="Tap", element=[100, 200])
finish(message="Done")"""

        // When
        val (thinking, action) = ModelResponseParser.parseThinkingAndAction(content)

        // Then
        assertEquals("Thinking...", thinking)
        assertEquals("""do(action="Tap", element=[100, 200])""", action)
    }

    @Test
    fun `parseThinkingAndAction_finishBeforeDo_returnsFinishAction`() {
        // Given - finish action appears before do
        val content = """Thinking...
finish(message="Done")
do(action="Tap", element=[100, 200])"""

        // When
        val (thinking, action) = ModelResponseParser.parseThinkingAndAction(content)

        // Then
        assertEquals("Thinking...", thinking)
        assertEquals("""finish(message="Done")""", action)
    }
}
