package com.flowmate.autoxiaoer.model

import java.io.IOException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.*
import io.kotest.core.spec.style.FunSpec

class DecisionModelClientTest : FunSpec({
    val client = DecisionModelClient(DecisionModelConfig(apiKey = "test-only"))

    test("request uses independent noul questions and structured state") {
        val state = Json.parseToJsonElement("""{"task":"收蛋并捐蛋"}""")
        val request = client.buildRequest(state, mapOf("collect" to DecisionQuestion("Relevant?", "Relevant", "Unrelated")))
        assertEquals(JsonPrimitive("microsoft/microsoft-decision-1"), request["model"])
        assertEquals(state, request["state"])
        val question = request.getValue("questions").jsonObject.getValue("collect").jsonObject
        assertEquals(JsonPrimitive("noul"), question["type"])
        assertEquals(setOf("true", "false"), question.getValue("criteria").jsonObject.keys)
        assertFalse(request.containsKey("messages"))
    }

    test("scores do not need to sum to one") {
        val scores = client.parseResponse("""{"answers":{"a":{"type":"noul","noul":0.9},"b":{"type":"noul","noul":0.8}}}""", setOf("a", "b"))
        assertEquals(mapOf("a" to 0.9, "b" to 0.8), scores)
    }

    test("reject missing unexpected or invalid answers") {
        listOf(
            """{"answers":{}}""",
            """{"answers":{"a":{"type":"choice","noul":0.9}}}""",
            """{"answers":{"a":{"type":"noul","noul":1.1}}}""",
            """{"answers":{"a":{"type":"noul","noul":-0.1}}}""",
            """{"answers":{"a":{"type":"noul","noul":"0.8"}}}""",
            """{"answers":{"a":{"type":"noul","noul":null}}}""",
            """{"answers":{"a":{"type":"noul","noul":0.8},"extra":{}}}""",
        ).forEach { body ->
            assertThrows(IOException::class.java) { client.parseResponse(body, setOf("a")) }
        }
    }

    test("empty questions skip network and credentials are redacted") {
        assertTrue(client.score(JsonObject(emptyMap()), emptyMap()).isEmpty())
        assertFalse(DecisionModelConfig(apiKey = "secret-value").toString().contains("secret-value"))
    }
})
