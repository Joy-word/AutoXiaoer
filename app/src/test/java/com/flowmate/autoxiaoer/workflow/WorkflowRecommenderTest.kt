package com.flowmate.autoxiaoer.workflow

import com.flowmate.autoxiaoer.model.DecisionScorer
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.*
import io.kotest.core.spec.style.FunSpec

class WorkflowRecommenderTest : FunSpec({
    val context = WorkflowDecisionContext(task = "收蛋并捐蛋")
    val candidates = listOf(WorkflowCandidate("a", "收蛋"), WorkflowCandidate("b", "捐蛋"))

    fun scorer(vararg scores: Double) = DecisionScorer { _, questions ->
        questions.keys.zip(scores.toList()).toMap()
    }

    test("multiple relevant workflows sorted independently and stable across catalog order") {
        val recommender = WorkflowRecommender(scorer(0.8, 0.9))
        val result = recommender.recommend(context, candidates)
        assertEquals(listOf("b", "a"), result.recommendations.map { it.candidate.id })
        assertEquals(result, recommender.recommend(context, candidates.reversed()))
        assertEquals(mapOf("a" to 0.8, "b" to 0.9), result.scores)
    }

    test("threshold is strict and permits no matches") {
        val result = WorkflowRecommender(scorer(0.5, 0.1)).recommend(context, candidates)
        assertEquals(RecommendationStatus.SCORED, result.status)
        assertTrue(result.recommendations.isEmpty())
    }

    test("first round and bounded raw history preserve guidance and unknown state") {
        var calls = 0
        val recommender = WorkflowRecommender(DecisionScorer { state, _ ->
            calls++
            val data = state.jsonObject
            if (calls == 1) {
                assertFalse(data.containsKey("latestPlan"))
            } else {
                assertEquals(1, data.getValue("recentToolRecords").jsonArray.size)
                assertTrue(data.toString().contains("latest-result"))
                assertTrue(data.toString().contains("repeat"))
                assertFalse(data.toString().contains("old-result"))
            }
            mapOf("workflow_0" to 0.9)
        }, WorkflowRecommendationConfig(maxRecentToolRecords = 1))
        recommender.recommend(context, candidates.take(1))
        val history = listOf("old-result", "latest-result").map { DecisionToolRecord("tool", JsonObject(emptyMap()), it) }
        recommender.recommend(context.copy(latestPlan = "plan", recentToolRecords = history, userGuidance = listOf("repeat")), candidates.take(1))
        assertEquals(2, calls)
    }

    test("empty duplicate and over budget catalogs do not call scorer") {
        val recommender = WorkflowRecommender(DecisionScorer { _, _ -> error("Must not call") }, WorkflowRecommendationConfig(maxInputChars = 1))
        assertEquals(RecommendationStatus.EMPTY_CATALOG, recommender.recommend(context, emptyList()).status)
        assertEquals(RecommendationStatus.UNAVAILABLE, recommender.recommend(context, candidates + candidates).status)
        assertEquals(RecommendationStatus.UNAVAILABLE, recommender.recommend(context, candidates).status)
    }

    test("errors invalid scores and timeouts fail open") {
        val scorers = listOf(
            DecisionScorer { _, _ -> throw IOException("offline") },
            scorer(Double.NaN, 0.9), scorer(1.1, 0.9), scorer(0.9),
            DecisionScorer { _, _ -> awaitCancellation() },
        )
        for (scorer in scorers) {
            val result = WorkflowRecommender(scorer, WorkflowRecommendationConfig(timeoutMillis = 20)).recommend(context, candidates)
            assertEquals(RecommendationStatus.UNAVAILABLE, result.status)
            assertTrue(result.recommendations.isEmpty())
        }
    }

    test("caller cancellation propagates including outer timeout") {
        assertThrows(CancellationException::class.java) {
            runBlocking {
                WorkflowRecommender(DecisionScorer { _, _ -> throw CancellationException("cancelled") }).recommend(context, candidates)
            }
        }
        assertThrows(CancellationException::class.java) {
            runBlocking {
                withTimeout(20) {
                    WorkflowRecommender(DecisionScorer { _, _ -> awaitCancellation() }).recommend(context, candidates)
                }
            }
        }
    }

    test("candidate count and description budgets are respected") {
        val result = WorkflowRecommender(scorer(0.8, 0.9), WorkflowRecommendationConfig(maxRecommendations = 1)).recommend(context, candidates)
        assertEquals(listOf("b"), result.recommendations.map { it.candidate.id })
        val bounded = WorkflowRecommender(scorer(0.8, 0.9), WorkflowRecommendationConfig(maxRecommendationChars = 1)).recommend(context, candidates)
        assertTrue(bounded.recommendations.isEmpty())
        assertEquals(2, bounded.scores.size)
    }
})
