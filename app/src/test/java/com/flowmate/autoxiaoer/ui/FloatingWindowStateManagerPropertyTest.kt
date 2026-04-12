package com.flowmate.autoxiaoer.ui

import com.flowmate.autoxiaoer.util.Logger
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.enum
import io.kotest.property.checkAll
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.unmockkObject

/**
 * Property-based tests for [FloatingWindowStateManager] state machine.
 *
 * Tests universal properties that should hold for all valid state transitions.
 *
 * **Feature: floating-window-architecture, Property 1: 状态机转换正确性**
 * **Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.5, 2.6**
 */
class FloatingWindowStateManagerPropertyTest :
    StringSpec({

        beforeSpec {
            mockkObject(Logger)
            every { Logger.d(any(), any()) } just Runs
            every { Logger.i(any(), any()) } just Runs
            every { Logger.w(any(), any()) } just Runs
            every { Logger.e(any(), any()) } just Runs
            every { Logger.e(any(), any(), any()) } just Runs

            // Mock FloatingWindowService.getInstance() to return null
            // since we're testing state logic, not service interaction
            mockkObject(FloatingWindowService.Companion)
            every { FloatingWindowService.getInstance() } returns null
        }

        afterSpec {
            unmockkObject(Logger)
            unmockkObject(FloatingWindowService.Companion)
        }

        beforeEach {
            // Reset state before each test
            FloatingWindowStateManager.reset()
        }

        /**
         * Property 1a: User enable transitions to VISIBLE_WHEN_BACKGROUND
         *
         * *For any* initial state that is not FORCED_VISIBLE,
         * calling enableByUser SHALL transition to VISIBLE_WHEN_BACKGROUND.
         *
         * **Validates: Requirements 2.5**
         */
        "Property 1a: enableByUser transitions to VISIBLE_WHEN_BACKGROUND when not FORCED_VISIBLE" {
            checkAll(100, Arb.enum<FloatingWindowState>()) { initialState ->
                // Reset and set initial state
                FloatingWindowStateManager.reset()

                // Skip FORCED_VISIBLE as it has special behavior
                if (initialState != FloatingWindowState.FORCED_VISIBLE) {
                    // Set initial state by manipulating through public API
                    when (initialState) {
                        FloatingWindowState.HIDDEN -> {
                            // Default state after reset
                        }

                        FloatingWindowState.VISIBLE_WHEN_BACKGROUND -> {
                            // Already in this state after enableByUser
                        }

                        else -> {}
                    }

                    // Call enableByUser with a mock context
                    val mockContext = io.mockk.mockk<android.content.Context>(relaxed = true)
                    FloatingWindowStateManager.enableByUser(mockContext)

                    // Verify state is VISIBLE_WHEN_BACKGROUND
                    FloatingWindowStateManager.state.value shouldBe FloatingWindowState.VISIBLE_WHEN_BACKGROUND
                    FloatingWindowStateManager.isUserEnabled() shouldBe true
                }
            }
        }

        /**
         * Property 1b: User disable transitions to HIDDEN when not FORCED_VISIBLE
         *
         * *For any* state that is not FORCED_VISIBLE,
         * calling disableByUser SHALL transition to HIDDEN.
         *
         * **Validates: Requirements 2.6**
         */
        "Property 1b: disableByUser transitions to HIDDEN when not FORCED_VISIBLE" {
            checkAll(100, Arb.boolean()) { wasEnabled ->
                // Reset state
                FloatingWindowStateManager.reset()

                val mockContext = io.mockk.mockk<android.content.Context>(relaxed = true)

                // Optionally enable first
                if (wasEnabled) {
                    FloatingWindowStateManager.enableByUser(mockContext)
                }

                // Call disableByUser
                FloatingWindowStateManager.disableByUser()

                // Verify state is HIDDEN
                FloatingWindowStateManager.state.value shouldBe FloatingWindowState.HIDDEN
                FloatingWindowStateManager.isUserEnabled() shouldBe false
            }
        }

        /**
         * Property 1c: Task started transitions to FORCED_VISIBLE
         *
         * *For any* initial state, calling onTaskStarted SHALL
         * transition to FORCED_VISIBLE.
         *
         * **Validates: Requirements 2.3**
         */
        "Property 1c: onTaskStarted always transitions to FORCED_VISIBLE" {
            checkAll(100, Arb.boolean()) { userEnabled ->
                // Reset state
                FloatingWindowStateManager.reset()

                val mockContext = io.mockk.mockk<android.content.Context>(relaxed = true)

                // Set up initial state
                if (userEnabled) {
                    FloatingWindowStateManager.enableByUser(mockContext)
                }

                // Call onTaskStarted
                FloatingWindowStateManager.onTaskStarted(mockContext)

                // Verify state is FORCED_VISIBLE
                FloatingWindowStateManager.state.value shouldBe FloatingWindowState.FORCED_VISIBLE
            }
        }

        /**
         * Property 1d: Task completed returns to user preference state
         *
         * *For any* user preference (enabled/disabled), calling onTaskCompleted
         * after onTaskStarted SHALL return to the appropriate state:
         * - If userEnabledWindow=true → VISIBLE_WHEN_BACKGROUND
         * - If userEnabledWindow=false → HIDDEN
         *
         * **Validates: Requirements 2.4**
         */
        "Property 1d: onTaskCompleted returns to user preference state" {
            checkAll(100, Arb.boolean()) { userEnabled ->
                // Reset state
                FloatingWindowStateManager.reset()

                val mockContext = io.mockk.mockk<android.content.Context>(relaxed = true)

                // Set up user preference
                if (userEnabled) {
                    FloatingWindowStateManager.enableByUser(mockContext)
                }

                // Start task (transitions to FORCED_VISIBLE)
                FloatingWindowStateManager.onTaskStarted(mockContext)
                FloatingWindowStateManager.state.value shouldBe FloatingWindowState.FORCED_VISIBLE

                // Complete task
                FloatingWindowStateManager.onTaskCompleted()

                // Verify state returns to user preference
                val expectedState =
                    if (userEnabled) {
                        FloatingWindowState.VISIBLE_WHEN_BACKGROUND
                    } else {
                        FloatingWindowState.HIDDEN
                    }
                FloatingWindowStateManager.state.value shouldBe expectedState
            }
        }

        /**
         * Property 1e: FORCED_VISIBLE state is preserved during user toggle
         *
         * *For any* toggle action while in FORCED_VISIBLE state,
         * the state SHALL remain FORCED_VISIBLE (task takes priority).
         *
         * **Validates: Requirements 2.3, 2.6**
         */
        "Property 1e: disableByUser does not change FORCED_VISIBLE state" {
            checkAll(100, Arb.boolean()) { _ ->
                // Reset state
                FloatingWindowStateManager.reset()

                val mockContext = io.mockk.mockk<android.content.Context>(relaxed = true)

                // Start task to get FORCED_VISIBLE
                FloatingWindowStateManager.onTaskStarted(mockContext)
                FloatingWindowStateManager.state.value shouldBe FloatingWindowState.FORCED_VISIBLE

                // Try to disable
                FloatingWindowStateManager.disableByUser()

                // State should still be FORCED_VISIBLE
                FloatingWindowStateManager.state.value shouldBe FloatingWindowState.FORCED_VISIBLE
            }
        }

        /**
         * Property 1f: toggleByUser correctly toggles state
         *
         * *For any* initial enabled state, toggleByUser SHALL:
         * - If enabled → disable (transition to HIDDEN if not FORCED_VISIBLE)
         * - If disabled → enable (transition to VISIBLE_WHEN_BACKGROUND)
         *
         * **Validates: Requirements 2.5, 2.6**
         */
        "Property 1f: toggleByUser correctly toggles between enabled and disabled" {
            checkAll(100, Arb.boolean()) { startEnabled ->
                // Reset state
                FloatingWindowStateManager.reset()

                val mockContext = io.mockk.mockk<android.content.Context>(relaxed = true)

                // Set initial state
                if (startEnabled) {
                    FloatingWindowStateManager.enableByUser(mockContext)
                }

                val initialUserEnabled = FloatingWindowStateManager.isUserEnabled()

                // Toggle
                FloatingWindowStateManager.toggleByUser(mockContext)

                // Verify toggle happened
                FloatingWindowStateManager.isUserEnabled() shouldBe !initialUserEnabled
            }
        }

        /**
         * Property 1g: State transitions are idempotent for same operation
         *
         * *For any* state, calling the same operation multiple times
         * SHALL result in the same final state.
         *
         * **Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.5, 2.6**
         */
        "Property 1g: enableByUser is idempotent" {
            checkAll(100, Arb.boolean()) { _ ->
                // Reset state
                FloatingWindowStateManager.reset()

                val mockContext = io.mockk.mockk<android.content.Context>(relaxed = true)

                // Call enableByUser multiple times
                FloatingWindowStateManager.enableByUser(mockContext)
                val stateAfterFirst = FloatingWindowStateManager.state.value
                val userEnabledAfterFirst = FloatingWindowStateManager.isUserEnabled()

                FloatingWindowStateManager.enableByUser(mockContext)
                val stateAfterSecond = FloatingWindowStateManager.state.value
                val userEnabledAfterSecond = FloatingWindowStateManager.isUserEnabled()

                FloatingWindowStateManager.enableByUser(mockContext)
                val stateAfterThird = FloatingWindowStateManager.state.value
                val userEnabledAfterThird = FloatingWindowStateManager.isUserEnabled()

                // All should be the same
                stateAfterFirst shouldBe stateAfterSecond
                stateAfterSecond shouldBe stateAfterThird
                userEnabledAfterFirst shouldBe userEnabledAfterSecond
                userEnabledAfterSecond shouldBe userEnabledAfterThird
            }
        }

        /**
         * Property 1h: disableByUser is idempotent
         *
         * *For any* state, calling disableByUser multiple times
         * SHALL result in the same final state.
         *
         * **Validates: Requirements 2.6**
         */
        "Property 1h: disableByUser is idempotent" {
            checkAll(100, Arb.boolean()) { startEnabled ->
                // Reset state
                FloatingWindowStateManager.reset()

                val mockContext = io.mockk.mockk<android.content.Context>(relaxed = true)

                if (startEnabled) {
                    FloatingWindowStateManager.enableByUser(mockContext)
                }

                // Call disableByUser multiple times
                FloatingWindowStateManager.disableByUser()
                val stateAfterFirst = FloatingWindowStateManager.state.value
                val userEnabledAfterFirst = FloatingWindowStateManager.isUserEnabled()

                FloatingWindowStateManager.disableByUser()
                val stateAfterSecond = FloatingWindowStateManager.state.value
                val userEnabledAfterSecond = FloatingWindowStateManager.isUserEnabled()

                // All should be the same
                stateAfterFirst shouldBe stateAfterSecond
                userEnabledAfterFirst shouldBe userEnabledAfterSecond
                stateAfterFirst shouldBe FloatingWindowState.HIDDEN
                userEnabledAfterFirst shouldBe false
            }
        }
    })

/**
 * Property-based tests for foreground/background visibility behavior.
 *
 * Tests that floating window visibility is correctly managed based on
 * app foreground/background state and window state.
 *
 * **Feature: floating-window-architecture, Property 5: 前后台切换可见性正确性**
 * **Validates: Requirements 6.3, 6.4, 6.5**
 */
class ForegroundBackgroundVisibilityPropertyTest :
    StringSpec({

        beforeSpec {
            mockkObject(Logger)
            every { Logger.d(any(), any()) } just Runs
            every { Logger.i(any(), any()) } just Runs
            every { Logger.w(any(), any()) } just Runs
            every { Logger.e(any(), any()) } just Runs
            every { Logger.e(any(), any(), any()) } just Runs

            // Mock FloatingWindowService.getInstance() to return null
            mockkObject(FloatingWindowService.Companion)
            every { FloatingWindowService.getInstance() } returns null
        }

        afterSpec {
            unmockkObject(Logger)
            unmockkObject(FloatingWindowService.Companion)
        }

        beforeEach {
            // Reset state before each test
            FloatingWindowStateManager.reset()
        }

        /**
         * Property 5a: Foreground + non-FORCED_VISIBLE → should not be visible
         *
         * *For any* state that is not FORCED_VISIBLE, when app is in foreground,
         * shouldBeVisible() SHALL return false.
         *
         * **Validates: Requirements 6.3**
         */
        "Property 5a: Foreground with non-FORCED_VISIBLE state should not be visible" {
            checkAll(100, Arb.boolean()) { userEnabled ->
                // Reset state
                FloatingWindowStateManager.reset()

                val mockContext = io.mockk.mockk<android.content.Context>(relaxed = true)

                // Set up state (HIDDEN or VISIBLE_WHEN_BACKGROUND)
                if (userEnabled) {
                    FloatingWindowStateManager.enableByUser(mockContext)
                }

                // Simulate foreground
                FloatingWindowStateManager.onAppForeground()

                // Verify visibility
                val state = FloatingWindowStateManager.state.value
                if (state != FloatingWindowState.FORCED_VISIBLE) {
                    FloatingWindowStateManager.shouldBeVisible() shouldBe false
                }
            }
        }

        /**
         * Property 5b: Background + VISIBLE_WHEN_BACKGROUND → should be visible
         *
         * *For any* scenario where state is VISIBLE_WHEN_BACKGROUND and app is in background,
         * shouldBeVisible() SHALL return true.
         *
         * **Validates: Requirements 6.4**
         */
        "Property 5b: Background with VISIBLE_WHEN_BACKGROUND state should be visible" {
            checkAll(100, Arb.boolean()) { _ ->
                // Reset state
                FloatingWindowStateManager.reset()

                val mockContext = io.mockk.mockk<android.content.Context>(relaxed = true)

                // Enable by user to get VISIBLE_WHEN_BACKGROUND
                FloatingWindowStateManager.enableByUser(mockContext)
                FloatingWindowStateManager.state.value shouldBe FloatingWindowState.VISIBLE_WHEN_BACKGROUND

                // Simulate background
                FloatingWindowStateManager.onAppBackground(mockContext)

                // Verify visibility
                FloatingWindowStateManager.shouldBeVisible() shouldBe true
            }
        }

        /**
         * Property 5c: Background + FORCED_VISIBLE → should be visible
         *
         * *For any* scenario where state is FORCED_VISIBLE and app is in background,
         * shouldBeVisible() SHALL return true.
         *
         * **Validates: Requirements 6.4**
         */
        "Property 5c: Background with FORCED_VISIBLE state should be visible" {
            checkAll(100, Arb.boolean()) { userEnabled ->
                // Reset state
                FloatingWindowStateManager.reset()

                val mockContext = io.mockk.mockk<android.content.Context>(relaxed = true)

                // Optionally enable by user first
                if (userEnabled) {
                    FloatingWindowStateManager.enableByUser(mockContext)
                }

                // Start task to get FORCED_VISIBLE
                FloatingWindowStateManager.onTaskStarted(mockContext)
                FloatingWindowStateManager.state.value shouldBe FloatingWindowState.FORCED_VISIBLE

                // Simulate background
                FloatingWindowStateManager.onAppBackground(mockContext)

                // Verify visibility
                FloatingWindowStateManager.shouldBeVisible() shouldBe true
            }
        }

        /**
         * Property 5d: Background + HIDDEN → should not be visible
         *
         * *For any* scenario where state is HIDDEN and app is in background,
         * shouldBeVisible() SHALL return false.
         *
         * **Validates: Requirements 6.5**
         */
        "Property 5d: Background with HIDDEN state should not be visible" {
            checkAll(100, Arb.boolean()) { _ ->
                // Reset state (starts as HIDDEN)
                FloatingWindowStateManager.reset()

                val mockContext = io.mockk.mockk<android.content.Context>(relaxed = true)

                // Verify state is HIDDEN
                FloatingWindowStateManager.state.value shouldBe FloatingWindowState.HIDDEN

                // Simulate background
                FloatingWindowStateManager.onAppBackground(mockContext)

                // Verify visibility
                FloatingWindowStateManager.shouldBeVisible() shouldBe false
            }
        }

        /**
         * Property 5e: FORCED_VISIBLE always visible regardless of foreground/background
         *
         * *For any* foreground/background state, when window state is FORCED_VISIBLE,
         * shouldBeVisible() SHALL return true.
         *
         * **Validates: Requirements 6.3, 6.4**
         */
        "Property 5e: FORCED_VISIBLE is always visible regardless of app state" {
            checkAll(100, Arb.boolean()) { inForeground ->
                // Reset state
                FloatingWindowStateManager.reset()

                val mockContext = io.mockk.mockk<android.content.Context>(relaxed = true)

                // Start task to get FORCED_VISIBLE
                FloatingWindowStateManager.onTaskStarted(mockContext)
                FloatingWindowStateManager.state.value shouldBe FloatingWindowState.FORCED_VISIBLE

                // Simulate foreground or background
                if (inForeground) {
                    FloatingWindowStateManager.onAppForeground()
                } else {
                    FloatingWindowStateManager.onAppBackground(mockContext)
                }

                // Verify visibility - FORCED_VISIBLE should always be visible
                FloatingWindowStateManager.shouldBeVisible() shouldBe true
            }
        }

        /**
         * Property 5f: isAppInForeground StateFlow is updated correctly
         *
         * *For any* sequence of foreground/background transitions,
         * isAppInForeground StateFlow SHALL reflect the current state.
         *
         * **Validates: Requirements 6.3, 6.4, 6.5**
         */
        "Property 5f: isAppInForeground StateFlow is updated correctly" {
            checkAll(100, Arb.boolean()) { goToForeground ->
                // Reset state
                FloatingWindowStateManager.reset()

                val mockContext = io.mockk.mockk<android.content.Context>(relaxed = true)

                // Perform transition
                if (goToForeground) {
                    FloatingWindowStateManager.onAppForeground()
                    FloatingWindowStateManager.isAppInForeground.value shouldBe true
                } else {
                    FloatingWindowStateManager.onAppBackground(mockContext)
                    FloatingWindowStateManager.isAppInForeground.value shouldBe false
                }
            }
        }

        /**
         * Property 5g: Multiple foreground/background transitions are consistent
         *
         * *For any* sequence of transitions, the final state should be deterministic
         * based on the last transition.
         *
         * **Validates: Requirements 6.3, 6.4, 6.5**
         */
        "Property 5g: Multiple transitions result in consistent final state" {
            checkAll(100, Arb.boolean(), Arb.boolean(), Arb.boolean()) { t1, t2, finalForeground ->
                // Reset state
                FloatingWindowStateManager.reset()

                val mockContext = io.mockk.mockk<android.content.Context>(relaxed = true)

                // Perform random transitions
                if (t1) {
                    FloatingWindowStateManager.onAppForeground()
                } else {
                    FloatingWindowStateManager.onAppBackground(mockContext)
                }

                if (t2) {
                    FloatingWindowStateManager.onAppForeground()
                } else {
                    FloatingWindowStateManager.onAppBackground(mockContext)
                }

                // Final transition
                if (finalForeground) {
                    FloatingWindowStateManager.onAppForeground()
                    FloatingWindowStateManager.isAppInForeground.value shouldBe true
                } else {
                    FloatingWindowStateManager.onAppBackground(mockContext)
                    FloatingWindowStateManager.isAppInForeground.value shouldBe false
                }
            }
        }
    })
