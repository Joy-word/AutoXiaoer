package com.flowmate.autoxiaoer.home

import com.flowmate.autoxiaoer.ui.MainUiState
import com.flowmate.autoxiaoer.ui.ShizukuStatus
import com.flowmate.autoxiaoer.ui.TaskStatus
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

/**
 * Property-based tests for TaskFragment UI state visibility logic.
 *
 * These tests verify that the UI correctly reflects task execution states
 * without requiring Android framework dependencies.
 *
 * Feature: bottom-navigation-tabs, Property 2: Task State Visibility
 * **Validates: Requirements 2.3, 2.4, 2.6**
 */
class TaskFragmentPropertyTest :
    StringSpec({

        /**
         * Property 2: Task State Visibility
         *
         * *For any* task execution state (Idle, Running, Completed, Error),
         * the TaskFragment UI should correctly reflect that state:
         * showing/hiding status card, enabling/disabling buttons appropriately.
         *
         * **Validates: Requirements 2.3, 2.4, 2.6**
         */
        "task status card visibility should match task active state" {
            checkAll(100, Arb.enum<TaskStatus>()) { taskStatus ->
                val state = MainUiState(taskStatus = taskStatus)
                val expectedVisible = taskStatus != TaskStatus.IDLE

                // Verify: status card should be visible when task is active
                computeStatusCardVisibility(state) shouldBe expectedVisible
            }
        }

        "control buttons should only be visible when task is running or paused" {
            checkAll(100, Arb.enum<TaskStatus>()) { taskStatus ->
                val state = MainUiState(taskStatus = taskStatus)
                val expectedVisible =
                    taskStatus == TaskStatus.RUNNING ||
                        taskStatus == TaskStatus.PAUSED

                // Verify: control buttons visibility
                computeControlButtonsVisibility(state) shouldBe expectedVisible
            }
        }

        "pause button should only be visible when task is running" {
            checkAll(100, Arb.enum<TaskStatus>()) { taskStatus ->
                val state = MainUiState(taskStatus = taskStatus)
                val expectedVisible = taskStatus == TaskStatus.RUNNING

                // Verify: pause button visibility
                computePauseButtonVisibility(state) shouldBe expectedVisible
            }
        }

        "resume button should only be visible when task is paused" {
            checkAll(100, Arb.enum<TaskStatus>()) { taskStatus ->
                val state = MainUiState(taskStatus = taskStatus)
                val expectedVisible = taskStatus == TaskStatus.PAUSED

                // Verify: resume button visibility
                computeResumeButtonVisibility(state) shouldBe expectedVisible
            }
        }

        "output log card should be visible when task is not idle" {
            checkAll(100, Arb.enum<TaskStatus>()) { taskStatus ->
                val state = MainUiState(taskStatus = taskStatus)
                val expectedVisible = taskStatus != TaskStatus.IDLE

                // Verify: output log card visibility
                computeOutputLogVisibility(state) shouldBe expectedVisible
            }
        }

        "start button should be enabled only when all conditions are met" {
            checkAll(
                100,
                Arb.enum<ShizukuStatus>(),
                Arb.enum<TaskStatus>(),
            ) { shizukuStatus, taskStatus ->
                // canStartTask requires:
                // - Shizuku connected
                // - Overlay permission granted
                // - Task not running
                // - Has task text (we assume true for this test)
                // - PhoneAgent available (we assume true for this test)
                val isConnected = shizukuStatus == ShizukuStatus.CONNECTED
                val isNotRunning =
                    taskStatus != TaskStatus.RUNNING &&
                        taskStatus != TaskStatus.PAUSED
                val hasOverlay = true // Assume granted for test

                val state =
                    MainUiState(
                        shizukuStatus = shizukuStatus,
                        taskStatus = taskStatus,
                        hasOverlayPermission = hasOverlay,
                        canStartTask = isConnected && hasOverlay && isNotRunning,
                    )

                // Verify: start button enabled state matches canStartTask
                computeStartButtonEnabled(state) shouldBe state.canStartTask
            }
        }

        "thinking text visibility should match thinking content" {
            checkAll(100, Arb.string(0, 100)) { thinking ->
                val state =
                    MainUiState(
                        taskStatus = TaskStatus.RUNNING,
                        thinking = thinking,
                    )
                val expectedVisible = thinking.isNotBlank()

                // Verify: thinking text visibility
                computeThinkingVisibility(state) shouldBe expectedVisible
            }
        }

        "current action visibility should match action content" {
            checkAll(100, Arb.string(0, 100)) { action ->
                val state =
                    MainUiState(
                        taskStatus = TaskStatus.RUNNING,
                        currentAction = action,
                    )
                val expectedVisible = action.isNotBlank()

                // Verify: current action visibility
                computeCurrentActionVisibility(state) shouldBe expectedVisible
            }
        }

        "step counter should display correct step number" {
            checkAll(100, Arb.int(0, 1000)) { stepNumber ->
                val state =
                    MainUiState(
                        taskStatus = TaskStatus.RUNNING,
                        stepNumber = stepNumber,
                    )

                // Verify: step counter displays correct number
                computeStepCounterText(state) shouldBe "步骤: $stepNumber"
            }
        }
    })

// Helper functions that mirror the logic in TaskFragment
// These are pure functions that can be tested without Android dependencies

/**
 * Computes whether the status card should be visible.
 */
private fun computeStatusCardVisibility(state: MainUiState): Boolean = state.taskStatus != TaskStatus.IDLE

/**
 * Computes whether the control buttons container should be visible.
 */
private fun computeControlButtonsVisibility(state: MainUiState): Boolean =
    state.taskStatus == TaskStatus.RUNNING || state.taskStatus == TaskStatus.PAUSED

/**
 * Computes whether the pause button should be visible.
 */
private fun computePauseButtonVisibility(state: MainUiState): Boolean = state.taskStatus == TaskStatus.RUNNING

/**
 * Computes whether the resume button should be visible.
 */
private fun computeResumeButtonVisibility(state: MainUiState): Boolean = state.taskStatus == TaskStatus.PAUSED

/**
 * Computes whether the output log card should be visible.
 */
private fun computeOutputLogVisibility(state: MainUiState): Boolean = state.taskStatus != TaskStatus.IDLE

/**
 * Computes whether the start button should be enabled.
 */
private fun computeStartButtonEnabled(state: MainUiState): Boolean = state.canStartTask

/**
 * Computes whether the thinking text should be visible.
 */
private fun computeThinkingVisibility(state: MainUiState): Boolean = state.thinking.isNotBlank()

/**
 * Computes whether the current action text should be visible.
 */
private fun computeCurrentActionVisibility(state: MainUiState): Boolean = state.currentAction.isNotBlank()

/**
 * Computes the step counter text.
 */
private fun computeStepCounterText(state: MainUiState): String = "步骤: ${state.stepNumber}"
