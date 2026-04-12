package com.flowmate.autoxiaoer.task

import com.flowmate.autoxiaoer.action.AgentAction
import com.flowmate.autoxiaoer.agent.PhoneAgentState
import com.flowmate.autoxiaoer.ui.TaskStatus
import com.flowmate.autoxiaoer.util.Logger
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.unmockkObject

/**
 * Property-based tests for [TaskExecutionManager].
 *
 * Tests universal properties that should hold for all valid inputs.
 *
 * **Feature: floating-window-architecture, Property 3: 任务状态一致性**
 * **Validates: Requirements 5.1, 9.4**
 */
class TaskExecutionManagerPropertyTest :
    StringSpec({

        beforeSpec {
            mockkObject(Logger)
            every { Logger.d(any(), any()) } just Runs
            every { Logger.i(any(), any()) } just Runs
            every { Logger.w(any(), any()) } just Runs
            every { Logger.e(any(), any()) } just Runs
            every { Logger.e(any(), any(), any()) } just Runs
        }

        afterSpec {
            unmockkObject(Logger)
        }

        /**
         * Property 3: PhoneAgentState to TaskStatus mapping consistency
         *
         * *For any* PhoneAgentState value, the mapping to TaskStatus SHALL follow
         * the defined rules:
         * - PhoneAgentState.IDLE → TaskStatus.IDLE
         * - PhoneAgentState.RUNNING → TaskStatus.RUNNING
         * - PhoneAgentState.PAUSED → TaskStatus.PAUSED
         * - PhoneAgentState.CANCELLED → TaskStatus.FAILED
         *
         * **Validates: Requirements 5.1, 9.4**
         */
        "Property 3: PhoneAgentState to TaskStatus mapping is consistent" {
            checkAll(100, Arb.enum<PhoneAgentState>()) { phoneAgentState ->
                val expectedTaskStatus =
                    when (phoneAgentState) {
                        PhoneAgentState.IDLE -> TaskStatus.IDLE
                        PhoneAgentState.RUNNING -> TaskStatus.RUNNING
                        PhoneAgentState.PAUSED -> TaskStatus.PAUSED
                        PhoneAgentState.CANCELLED -> TaskStatus.FAILED
                    }

                val actualTaskStatus = TaskExecutionManager.mapPhoneAgentStateToTaskStatus(phoneAgentState)

                actualTaskStatus shouldBe expectedTaskStatus
            }
        }

        /**
         * Property 3b: Mapping is deterministic
         *
         * *For any* PhoneAgentState value, calling mapPhoneAgentStateToTaskStatus multiple times
         * SHALL always return the same TaskStatus.
         *
         * **Validates: Requirements 5.1, 9.4**
         */
        "Property 3b: PhoneAgentState to TaskStatus mapping is deterministic" {
            checkAll(100, Arb.enum<PhoneAgentState>()) { phoneAgentState ->
                val result1 = TaskExecutionManager.mapPhoneAgentStateToTaskStatus(phoneAgentState)
                val result2 = TaskExecutionManager.mapPhoneAgentStateToTaskStatus(phoneAgentState)
                val result3 = TaskExecutionManager.mapPhoneAgentStateToTaskStatus(phoneAgentState)

                result1 shouldBe result2
                result2 shouldBe result3
            }
        }

        /**
         * Property 3c: All PhoneAgentState values have valid mappings
         *
         * *For any* PhoneAgentState value, the mapping SHALL produce a valid TaskStatus
         * (not throw an exception).
         *
         * **Validates: Requirements 5.1, 9.4**
         */
        "Property 3c: All PhoneAgentState values map to valid TaskStatus" {
            checkAll(100, Arb.enum<PhoneAgentState>()) { phoneAgentState ->
                val taskStatus = TaskExecutionManager.mapPhoneAgentStateToTaskStatus(phoneAgentState)

                // Verify the result is one of the expected TaskStatus values
                val validStatuses =
                    setOf(
                        TaskStatus.IDLE,
                        TaskStatus.RUNNING,
                        TaskStatus.PAUSED,
                        TaskStatus.FAILED,
                    )

                (taskStatus in validStatuses) shouldBe true
            }
        }
    })

/**
 * Property-based tests for TaskExecutionState data class.
 *
 * **Feature: floating-window-architecture, Property 3: 任务状态一致性**
 * **Validates: Requirements 9.1, 9.2, 9.3**
 */
class TaskExecutionStatePropertyTest :
    StringSpec({

        beforeSpec {
            mockkObject(Logger)
            every { Logger.d(any(), any()) } just Runs
            every { Logger.i(any(), any()) } just Runs
            every { Logger.w(any(), any()) } just Runs
            every { Logger.e(any(), any()) } just Runs
        }

        afterSpec {
            unmockkObject(Logger)
        }

        /**
         * Property: TaskExecutionState default values are correct
         *
         * *For any* newly created TaskExecutionState with default constructor,
         * all fields SHALL have their expected default values.
         *
         * **Validates: Requirements 9.1**
         */
        "TaskExecutionState default values are correct" {
            val state = TaskExecutionState()

            state.status shouldBe TaskStatus.IDLE
            state.stepNumber shouldBe 0
            state.thinking shouldBe ""
            state.currentAction shouldBe ""
            state.resultMessage shouldBe ""
            state.taskDescription shouldBe ""
        }

        /**
         * Property: TaskExecutionState copy preserves unchanged fields
         *
         * *For any* TaskExecutionState, copying with a single field change
         * SHALL preserve all other fields.
         *
         * **Validates: Requirements 9.1, 9.2**
         */
        "TaskExecutionState copy preserves unchanged fields" {
            checkAll(100, Arb.enum<TaskStatus>()) { newStatus ->
                val original =
                    TaskExecutionState(
                        status = TaskStatus.IDLE,
                        stepNumber = 5,
                        thinking = "test thinking",
                        currentAction = "test action",
                        resultMessage = "test result",
                        taskDescription = "test description",
                    )

                val copied = original.copy(status = newStatus)

                copied.status shouldBe newStatus
                copied.stepNumber shouldBe original.stepNumber
                copied.thinking shouldBe original.thinking
                copied.currentAction shouldBe original.currentAction
                copied.resultMessage shouldBe original.resultMessage
                copied.taskDescription shouldBe original.taskDescription
            }
        }
    })

/**
 * Property-based tests for StateFlow state propagation correctness.
 *
 * Tests that PhoneAgentListener callbacks correctly update the StateFlow
 * and all observers receive the same state.
 *
 * **Feature: floating-window-architecture, Property 2: StateFlow 状态传播正确性**
 * **Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 5.5**
 */
class StateFlowPropagationPropertyTest :
    StringSpec({

        beforeSpec {
            mockkObject(Logger)
            every { Logger.d(any(), any()) } just Runs
            every { Logger.i(any(), any()) } just Runs
            every { Logger.w(any(), any()) } just Runs
            every { Logger.e(any(), any()) } just Runs
            every { Logger.e(any(), any(), any()) } just Runs
        }

        afterSpec {
            unmockkObject(Logger)
            // Reset TaskExecutionManager state after tests
            TaskExecutionManager.resetTask()
        }

        /**
         * Property 2a: onStepStarted updates stepNumber and adds step to list
         *
         * *For any* valid step number, calling onStepStarted SHALL:
         * - Update taskState.stepNumber to the new value
         * - Add a new TaskStep to the steps list
         * - Clear thinking and currentAction fields
         *
         * **Validates: Requirements 3.2, 9.5**
         */
        "Property 2a: onStepStarted updates stepNumber and adds step to list" {
            checkAll(100, Arb.int(1..100)) { stepNumber ->
                // Reset state before each test
                TaskExecutionManager.resetTask()

                // Call the callback
                TaskExecutionManager.onStepStarted(stepNumber)

                // Verify taskState is updated
                val state = TaskExecutionManager.taskState.value
                state.stepNumber shouldBe stepNumber
                state.thinking shouldBe ""
                state.currentAction shouldBe ""

                // Verify step is added to list
                val steps = TaskExecutionManager.steps.value
                steps.isNotEmpty() shouldBe true
                steps.last().stepNumber shouldBe stepNumber
            }
        }

        /**
         * Property 2b: onThinkingUpdate updates thinking field
         *
         * *For any* thinking text, calling onThinkingUpdate SHALL:
         * - Update taskState.thinking to the new value
         * - Update the last step's thinking in the steps list
         *
         * **Validates: Requirements 3.3, 9.6**
         */
        "Property 2b: onThinkingUpdate updates thinking field" {
            checkAll(100, Arb.string(0..100)) { thinking ->
                // Reset and add a step first
                TaskExecutionManager.resetTask()
                TaskExecutionManager.onStepStarted(1)

                // Call the callback
                TaskExecutionManager.onThinkingUpdate(thinking)

                // Verify taskState is updated
                val state = TaskExecutionManager.taskState.value
                state.thinking shouldBe thinking

                // Verify last step's thinking is updated
                val steps = TaskExecutionManager.steps.value
                steps.isNotEmpty() shouldBe true
                steps.last().thinking shouldBe thinking
            }
        }

        /**
         * Property 2c: onTaskCompleted updates status to COMPLETED
         *
         * *For any* completion message, calling onTaskCompleted SHALL:
         * - Update taskState.status to COMPLETED
         * - Update taskState.resultMessage to the message
         *
         * **Validates: Requirements 3.5, 9.4**
         */
        "Property 2c: onTaskCompleted updates status to COMPLETED" {
            checkAll(100, Arb.string(0..100)) { message ->
                // Reset state before each test
                TaskExecutionManager.resetTask()

                // Call the callback
                TaskExecutionManager.onTaskCompleted(message)

                // Verify taskState is updated
                val state = TaskExecutionManager.taskState.value
                state.status shouldBe TaskStatus.COMPLETED
                state.resultMessage shouldBe message
            }
        }

        /**
         * Property 2d: onTaskFailed updates status to FAILED
         *
         * *For any* error message, calling onTaskFailed SHALL:
         * - Update taskState.status to FAILED
         * - Update taskState.resultMessage to the error
         *
         * **Validates: Requirements 3.6, 9.4**
         */
        "Property 2d: onTaskFailed updates status to FAILED" {
            checkAll(100, Arb.string(0..100)) { error ->
                // Reset state before each test
                TaskExecutionManager.resetTask()

                // Call the callback
                TaskExecutionManager.onTaskFailed(error)

                // Verify taskState is updated
                val state = TaskExecutionManager.taskState.value
                state.status shouldBe TaskStatus.FAILED
                state.resultMessage shouldBe error
            }
        }

        /**
         * Property 2e: onTaskPaused updates status to PAUSED
         *
         * *For any* step number, calling onTaskPaused SHALL:
         * - Update taskState.status to PAUSED
         *
         * **Validates: Requirements 3.7, 9.4**
         */
        "Property 2e: onTaskPaused updates status to PAUSED" {
            checkAll(100, Arb.int(1..100)) { stepNumber ->
                // Reset state before each test
                TaskExecutionManager.resetTask()

                // Call the callback
                TaskExecutionManager.onTaskPaused(stepNumber)

                // Verify taskState is updated
                val state = TaskExecutionManager.taskState.value
                state.status shouldBe TaskStatus.PAUSED
            }
        }

        /**
         * Property 2f: onTaskResumed updates status to RUNNING
         *
         * *For any* step number, calling onTaskResumed SHALL:
         * - Update taskState.status to RUNNING
         *
         * **Validates: Requirements 3.7, 9.4**
         */
        "Property 2f: onTaskResumed updates status to RUNNING" {
            checkAll(100, Arb.int(1..100)) { stepNumber ->
                // Reset state before each test
                TaskExecutionManager.resetTask()

                // Call the callback
                TaskExecutionManager.onTaskResumed(stepNumber)

                // Verify taskState is updated
                val state = TaskExecutionManager.taskState.value
                state.status shouldBe TaskStatus.RUNNING
            }
        }

        /**
         * Property 2g: Multiple observers receive the same state
         *
         * *For any* state update, all observers of taskState SHALL
         * receive the same state value.
         *
         * **Validates: Requirements 5.5**
         */
        "Property 2g: Multiple observers receive the same state" {
            checkAll(100, Arb.int(1..100), Arb.string(0..50)) { stepNumber, thinking ->
                // Reset state before each test
                TaskExecutionManager.resetTask()

                // Simulate a sequence of callbacks
                TaskExecutionManager.onStepStarted(stepNumber)
                TaskExecutionManager.onThinkingUpdate(thinking)

                // Get state from multiple access points
                val state1 = TaskExecutionManager.taskState.value
                val state2 = TaskExecutionManager.taskState.value

                // Both should be identical
                state1 shouldBe state2
                state1.stepNumber shouldBe stepNumber
                state1.thinking shouldBe thinking
            }
        }
    })

/**
 * Property-based tests for TaskStep data class.
 *
 * **Feature: floating-window-architecture, Property 6: 步骤列表单调递增**
 * **Validates: Requirements 3.2, 9.5**
 */
class TaskStepPropertyTest :
    StringSpec({

        beforeSpec {
            mockkObject(Logger)
            every { Logger.d(any(), any()) } just Runs
            every { Logger.i(any(), any()) } just Runs
            every { Logger.w(any(), any()) } just Runs
            every { Logger.e(any(), any()) } just Runs
        }

        afterSpec {
            unmockkObject(Logger)
        }

        /**
         * Property: TaskStep equality is based on all fields
         *
         * *For any* two TaskStep instances with identical fields,
         * they SHALL be equal.
         *
         * **Validates: Requirements 9.5**
         */
        "TaskStep equality is based on all fields" {
            checkAll(100, Arb.enum<TaskStatus>()) { _ ->
                val step1 =
                    TaskStep(
                        stepNumber = 1,
                        thinking = "thinking",
                        action = "action",
                    )
                val step2 =
                    TaskStep(
                        stepNumber = 1,
                        thinking = "thinking",
                        action = "action",
                    )

                step1 shouldBe step2
            }
        }
    })
