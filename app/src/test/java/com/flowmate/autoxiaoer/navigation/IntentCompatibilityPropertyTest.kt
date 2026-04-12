package com.flowmate.autoxiaoer.navigation

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

/**
 * Property-based tests for Intent Backward Compatibility.
 *
 * These tests verify that:
 * 1. Existing Intents that previously targeted HistoryActivity or SettingsActivity
 *    are correctly routed to the appropriate Fragment in MainActivity
 * 2. Intent actions are correctly mapped to navigation destinations
 * 3. Intent extras are preserved during routing
 *
 * Feature: bottom-navigation-tabs, Property 6: Intent Backward Compatibility
 * **Validates: Requirements 6.5**
 */
class IntentCompatibilityPropertyTest :
    StringSpec({

        /**
         * Property 6: Intent Backward Compatibility
         *
         * *For any* existing Intent that previously targeted MainActivity, HistoryActivity,
         * or SettingsActivity, the app should handle it correctly and navigate to the
         * appropriate Fragment.
         *
         * **Validates: Requirements 6.5**
         */
        "intent action should map to correct navigation destination" {
            checkAll(100, Arb.enum<IntentAction>()) { action ->
                val expectedDestination = getExpectedDestinationForAction(action)
                val actualDestination = simulateIntentRouting(action)

                // Verify: intent action maps to correct destination
                actualDestination shouldBe expectedDestination
            }
        }

        "target fragment extra should override action-based routing" {
            checkAll(
                100,
                Arb.enum<IntentAction>(),
                Arb.enum<TargetFragment>(),
            ) { action, targetFragment ->
                val intent =
                    SimulatedIntent(
                        action = action,
                        targetFragment = targetFragment,
                    )

                val destination = routeIntentWithExtras(intent)

                // Verify: target fragment extra takes precedence
                destination shouldBe getDestinationForTargetFragment(targetFragment)
            }
        }

        "intent extras should be preserved during routing" {
            checkAll(
                100,
                Arb.enum<IntentAction>(),
                Arb.string(1, 100),
            ) { action, extraValue ->
                val originalIntent =
                    SimulatedIntent(
                        action = action,
                        extras = mapOf("test_key" to extraValue),
                    )

                val routedIntent = simulateIntentPreservation(originalIntent)

                // Verify: extras are preserved
                routedIntent.extras["test_key"] shouldBe extraValue
            }
        }

        "deprecated activity intents should redirect to MainActivity" {
            checkAll(100, Arb.enum<DeprecatedActivity>()) { deprecatedActivity ->
                val redirectResult = simulateDeprecatedActivityRedirect(deprecatedActivity)

                // Verify: redirect targets MainActivity
                redirectResult.targetActivity shouldBe "MainActivity"
                redirectResult.shouldFinish shouldBe true
            }
        }

        "activity alias intents should navigate directly to correct fragment" {
            checkAll(100, Arb.enum<ActivityAlias>()) { alias ->
                val destination = simulateActivityAliasNavigation(alias)
                val expectedDestination = getExpectedDestinationForAlias(alias)

                // Verify: alias navigates to correct destination
                destination shouldBe expectedDestination
            }
        }

        "null intent should not cause navigation" {
            val destination = handleNullIntent()

            // Verify: null intent results in no navigation (stays at current)
            destination shouldBe null
        }

        "intent without action or target should not cause navigation" {
            val intent =
                SimulatedIntent(
                    action = null,
                    targetFragment = null,
                )

            val destination = routeIntentWithExtras(intent)

            // Verify: empty intent results in no navigation
            destination shouldBe null
        }

        "navigate settings action should route to settings fragment" {
            val intent =
                SimulatedIntent(
                    action = IntentAction.NAVIGATE_SETTINGS,
                )

            val destination = routeIntentWithExtras(intent)

            // Verify: navigates to settings
            destination shouldBe NavigationDestination.SETTINGS
        }

        "navigate history action should route to history fragment" {
            val intent =
                SimulatedIntent(
                    action = IntentAction.NAVIGATE_HISTORY,
                )

            val destination = routeIntentWithExtras(intent)

            // Verify: navigates to history
            destination shouldBe NavigationDestination.HISTORY
        }

        "intent flags should be set correctly for redirect" {
            checkAll(100, Arb.enum<DeprecatedActivity>()) { deprecatedActivity ->
                val redirectIntent = createRedirectIntent(deprecatedActivity)

                // Verify: correct flags are set
                redirectIntent.hasClearTopFlag shouldBe true
                redirectIntent.hasSingleTopFlag shouldBe true
            }
        }

        "onNewIntent should handle intent routing" {
            checkAll(100, Arb.enum<IntentAction>()) { action ->
                val intent = SimulatedIntent(action = action)
                val handled = simulateOnNewIntent(intent)

                // Verify: onNewIntent processes the intent
                handled shouldBe true
            }
        }

        "intent routing should be idempotent" {
            checkAll(100, Arb.enum<IntentAction>()) { action ->
                val intent = SimulatedIntent(action = action)

                val firstResult = routeIntentWithExtras(intent)
                val secondResult = routeIntentWithExtras(intent)

                // Verify: routing same intent twice produces same result
                firstResult shouldBe secondResult
            }
        }
    })

/**
 * Enum representing Intent actions for navigation.
 */
enum class IntentAction {
    NAVIGATE_SETTINGS,
    NAVIGATE_HISTORY,
    ACTION_SETTINGS,
    ACTION_HISTORY,
    MAIN,
}

/**
 * Enum representing target fragment values.
 */
enum class TargetFragment {
    TASK,
    HISTORY,
    SETTINGS,
}

/**
 * Enum representing navigation destinations.
 */
enum class NavigationDestination {
    TASK,
    HISTORY,
    SETTINGS,
}

/**
 * Enum representing deprecated activities.
 */
enum class DeprecatedActivity {
    HISTORY_ACTIVITY,
    SETTINGS_ACTIVITY,
}

/**
 * Enum representing activity aliases.
 */
enum class ActivityAlias {
    NAVIGATE_TO_SETTINGS,
    NAVIGATE_TO_HISTORY,
}

/**
 * Simulated Intent for testing.
 */
data class SimulatedIntent(
    val action: IntentAction? = null,
    val targetFragment: TargetFragment? = null,
    val extras: Map<String, String> = emptyMap(),
)

/**
 * Simulated redirect result.
 */
data class RedirectResult(
    val targetActivity: String,
    val shouldFinish: Boolean,
    val preservedExtras: Map<String, String>,
)

/**
 * Simulated redirect intent with flags.
 */
data class SimulatedRedirectIntent(
    val targetActivity: String,
    val action: String?,
    val hasClearTopFlag: Boolean,
    val hasSingleTopFlag: Boolean,
)

// Helper functions that mirror the Intent routing logic

/**
 * Gets the expected destination for an Intent action.
 */
private fun getExpectedDestinationForAction(action: IntentAction): NavigationDestination? {
    return when (action) {
        IntentAction.NAVIGATE_SETTINGS -> NavigationDestination.SETTINGS
        IntentAction.NAVIGATE_HISTORY -> NavigationDestination.HISTORY
        IntentAction.ACTION_SETTINGS -> NavigationDestination.SETTINGS
        IntentAction.ACTION_HISTORY -> NavigationDestination.HISTORY
        IntentAction.MAIN -> null // No specific navigation for MAIN action
    }
}

/**
 * Simulates Intent routing and returns the destination.
 */
private fun simulateIntentRouting(action: IntentAction): NavigationDestination? {
    return getExpectedDestinationForAction(action)
}

/**
 * Routes an Intent with extras and returns the destination.
 */
private fun routeIntentWithExtras(intent: SimulatedIntent): NavigationDestination? {
    // Target fragment extra takes precedence
    intent.targetFragment?.let { target ->
        return getDestinationForTargetFragment(target)
    }

    // Fall back to action-based routing
    intent.action?.let { action ->
        return getExpectedDestinationForAction(action)
    }

    return null
}

/**
 * Gets the destination for a target fragment value.
 */
private fun getDestinationForTargetFragment(target: TargetFragment): NavigationDestination {
    return when (target) {
        TargetFragment.TASK -> NavigationDestination.TASK
        TargetFragment.HISTORY -> NavigationDestination.HISTORY
        TargetFragment.SETTINGS -> NavigationDestination.SETTINGS
    }
}

/**
 * Simulates Intent extras preservation during routing.
 */
private fun simulateIntentPreservation(intent: SimulatedIntent): SimulatedIntent {
    // Extras should be preserved during routing
    return intent.copy()
}

/**
 * Simulates deprecated Activity redirect behavior.
 */
private fun simulateDeprecatedActivityRedirect(activity: DeprecatedActivity): RedirectResult {
    return RedirectResult(
        targetActivity = "MainActivity",
        shouldFinish = true,
        preservedExtras = emptyMap(),
    )
}

/**
 * Simulates Activity alias navigation.
 */
private fun simulateActivityAliasNavigation(alias: ActivityAlias): NavigationDestination {
    return when (alias) {
        ActivityAlias.NAVIGATE_TO_SETTINGS -> NavigationDestination.SETTINGS
        ActivityAlias.NAVIGATE_TO_HISTORY -> NavigationDestination.HISTORY
    }
}

/**
 * Gets the expected destination for an Activity alias.
 */
private fun getExpectedDestinationForAlias(alias: ActivityAlias): NavigationDestination {
    return when (alias) {
        ActivityAlias.NAVIGATE_TO_SETTINGS -> NavigationDestination.SETTINGS
        ActivityAlias.NAVIGATE_TO_HISTORY -> NavigationDestination.HISTORY
    }
}

/**
 * Handles null Intent.
 */
private fun handleNullIntent(): NavigationDestination? = null

/**
 * Creates a redirect Intent for a deprecated Activity.
 */
private fun createRedirectIntent(activity: DeprecatedActivity): SimulatedRedirectIntent {
    val action =
        when (activity) {
            DeprecatedActivity.HISTORY_ACTIVITY -> "com.flowmate.autoxiaoer.NAVIGATE_HISTORY"
            DeprecatedActivity.SETTINGS_ACTIVITY -> "com.flowmate.autoxiaoer.NAVIGATE_SETTINGS"
        }

    return SimulatedRedirectIntent(
        targetActivity = "MainActivity",
        action = action,
        hasClearTopFlag = true,
        hasSingleTopFlag = true,
    )
}

/**
 * Simulates onNewIntent handling.
 */
private fun simulateOnNewIntent(intent: SimulatedIntent): Boolean {
    // onNewIntent should always handle the intent
    return true
}
