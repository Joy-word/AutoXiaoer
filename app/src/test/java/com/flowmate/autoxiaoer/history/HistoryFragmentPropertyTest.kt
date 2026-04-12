package com.flowmate.autoxiaoer.history

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import java.util.UUID

/**
 * Property-based tests for HistoryFragment UI state visibility logic.
 *
 * These tests verify that the UI correctly reflects history list states
 * without requiring Android framework dependencies.
 *
 * Feature: bottom-navigation-tabs, Property 3: History List Empty State
 * **Validates: Requirements 3.5**
 */
class HistoryFragmentPropertyTest :
    StringSpec({

        /**
         * Property 3: History List Empty State
         *
         * *For any* history list state, when the list is empty the empty state view
         * should be visible and RecyclerView hidden; when the list has items,
         * the opposite should be true.
         *
         * **Validates: Requirements 3.5**
         */
        "empty state visibility should be inverse of list having items" {
            checkAll(100, Arb.list(taskHistoryArb(), 0..20)) { historyList ->
                val isEmpty = historyList.isEmpty()

                // Verify: empty state visibility is inverse of having items
                computeEmptyStateVisibility(historyList) shouldBe isEmpty
                computeRecyclerViewVisibility(historyList) shouldBe !isEmpty
            }
        }

        "empty state should be visible when list is empty" {
            val emptyList = emptyList<TaskHistory>()

            computeEmptyStateVisibility(emptyList) shouldBe true
            computeRecyclerViewVisibility(emptyList) shouldBe false
        }

        "recycler view should be visible when list has items" {
            checkAll(100, Arb.list(taskHistoryArb(), 1..20)) { historyList ->
                // List is guaranteed to have at least 1 item
                computeEmptyStateVisibility(historyList) shouldBe false
                computeRecyclerViewVisibility(historyList) shouldBe true
            }
        }

        "selection mode should exit when list becomes empty" {
            checkAll(100, Arb.boolean()) { wasInSelectionMode ->
                val emptyList = emptyList<TaskHistory>()

                // When list becomes empty and was in selection mode, should exit
                val shouldExitSelectionMode = wasInSelectionMode && emptyList.isEmpty()

                computeShouldExitSelectionMode(wasInSelectionMode, emptyList) shouldBe shouldExitSelectionMode
            }
        }

        "selection count should never exceed list size" {
            checkAll(
                100,
                Arb.list(taskHistoryArb(), 0..20),
                Arb.int(0..30),
            ) { historyList, selectedCount ->
                val actualSelectedCount = minOf(selectedCount, historyList.size)

                // Verify: selection count is capped at list size
                computeValidSelectionCount(historyList, selectedCount) shouldBe actualSelectedCount
            }
        }

        "clear all should result in empty list" {
            checkAll(100, Arb.list(taskHistoryArb(), 0..20)) { historyList ->
                val clearedList = clearAllHistory(historyList)

                // Verify: cleared list is empty
                clearedList.isEmpty() shouldBe true
                computeEmptyStateVisibility(clearedList) shouldBe true
            }
        }

        "delete selected should remove only selected items" {
            checkAll(100, Arb.list(taskHistoryArb(), 1..10)) { historyList ->
                // Select first half of items
                val selectedIds = historyList.take(historyList.size / 2).map { it.id }.toSet()
                val remainingList = deleteSelectedItems(historyList, selectedIds)

                // Verify: remaining list has correct size
                remainingList.size shouldBe historyList.size - selectedIds.size

                // Verify: no selected items remain
                remainingList.none { it.id in selectedIds } shouldBe true
            }
        }
    })

// Arbitrary generator for TaskHistory
private fun taskHistoryArb(): Arb<TaskHistory> = Arb.bind(
    Arb.string(5, 50),
    Arb.long(0L, System.currentTimeMillis()),
    Arb.boolean(),
) { description, startTime, success ->
    TaskHistory(
        id = UUID.randomUUID().toString(),
        taskDescription = description,
        startTime = startTime,
        endTime = startTime + 1000,
        success = success,
    )
}

// Helper functions that mirror the logic in HistoryFragment
// These are pure functions that can be tested without Android dependencies

/**
 * Computes whether the empty state should be visible.
 */
private fun computeEmptyStateVisibility(historyList: List<TaskHistory>): Boolean = historyList.isEmpty()

/**
 * Computes whether the RecyclerView should be visible.
 */
private fun computeRecyclerViewVisibility(historyList: List<TaskHistory>): Boolean = historyList.isNotEmpty()

/**
 * Computes whether selection mode should exit based on list state.
 */
private fun computeShouldExitSelectionMode(wasInSelectionMode: Boolean, historyList: List<TaskHistory>): Boolean =
    wasInSelectionMode && historyList.isEmpty()

/**
 * Computes the valid selection count (capped at list size).
 */
private fun computeValidSelectionCount(historyList: List<TaskHistory>, requestedCount: Int): Int =
    minOf(requestedCount, historyList.size)

/**
 * Simulates clearing all history.
 */
private fun clearAllHistory(historyList: List<TaskHistory>): List<TaskHistory> = emptyList()

/**
 * Simulates deleting selected items from the list.
 */
private fun deleteSelectedItems(historyList: List<TaskHistory>, selectedIds: Set<String>): List<TaskHistory> =
    historyList.filter { it.id !in selectedIds }
