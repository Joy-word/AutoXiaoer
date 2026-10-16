package com.flowmate.autoxiaoer.model

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

data class DecisionModelConfig(
    val apiKey: String,
    val baseUrl: String = "https://openrouter.ai/api/alpha",
    val modelName: String = "microsoft/microsoft-decision-1",
    val timeoutMillis: Long = 5000,
) {
    init {
        require(apiKey.isNotBlank()) { "Decision model API key is required" }
        require(modelName.isNotBlank()) { "Decision model name is required" }
        require(timeoutMillis > 0) { "Timeout must be positive" }
    }

    override fun toString(): String =
        "DecisionModelConfig(baseUrl=$baseUrl, modelName=$modelName, timeoutMillis=$timeoutMillis, apiKey=<redacted>)"
}

data class DecisionQuestion(
    val instructions: String,
    val trueCriteria: String,
    val falseCriteria: String,
)

fun interface DecisionScorer {
    suspend fun score(state: JsonElement, questions: Map<String, DecisionQuestion>): Map<String, Double>
}

/** Uses OpenRouter's Decisions API, which is separate from chat/completions. */
class DecisionModelClient(
    private val config: DecisionModelConfig,
    client: OkHttpClient = OkHttpClient(),
) : DecisionScorer {
    private val httpClient = client.newBuilder()
        .callTimeout(config.timeoutMillis, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(false)
        .build()

    override suspend fun score(
        state: JsonElement,
        questions: Map<String, DecisionQuestion>,
    ): Map<String, Double> {
        if (questions.isEmpty()) return emptyMap()
        require(questions.keys.all { it.isNotBlank() }) { "Question IDs must not be blank" }
        val request = Request.Builder()
            .url("${config.baseUrl.trimEnd('/')}/decisions")
            .header("Authorization", "Bearer ${config.apiKey}")
            .post(buildRequest(state, questions).toString().toRequestBody("application/json".toMediaType()))
            .build()
        return withTimeout(config.timeoutMillis) {
            suspendCancellableCoroutine { continuation ->
                val call = httpClient.newCall(request)
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        continuation.resumeWith(Result.failure(e))
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val result = runCatching {
                            response.use {
                                if (!it.isSuccessful) throw IOException("Decision API HTTP ${it.code}")
                                parseResponse(it.body?.string() ?: throw IOException("Empty decision response"), questions.keys)
                            }
                        }
                        continuation.resumeWith(result)
                    }
                })
            }
        }
    }

    internal fun buildRequest(state: JsonElement, questions: Map<String, DecisionQuestion>): JsonObject =
        buildJsonObject {
            put("model", config.modelName)
            put("state", state)
            put("questions", buildJsonObject {
                questions.forEach { (id, question) ->
                    put(id, buildJsonObject {
                        put("type", "noul")
                        put("instructions", question.instructions)
                        put("criteria", buildJsonObject {
                            put("true", question.trueCriteria)
                            put("false", question.falseCriteria)
                        })
                    })
                }
            })
        }

    internal fun parseResponse(body: String, expectedIds: Set<String>): Map<String, Double> {
        val root = Json.parseToJsonElement(body) as? JsonObject
            ?: throw IOException("Invalid decision response")
        val answers = root["answers"] as? JsonObject ?: throw IOException("Missing decision answers")
        if (answers.keys != expectedIds) throw IOException("Decision answer IDs do not match questions")
        return expectedIds.associateWith { id ->
            val answer = answers[id] as? JsonObject ?: throw IOException("Invalid decision answer")
            if (answer["type"] != JsonPrimitive("noul")) throw IOException("Expected independent noul answer")
            val value = (answer["noul"] as? JsonPrimitive)?.takeUnless { it.isString }?.doubleOrNull
                ?: throw IOException("Missing decision score")
            if (!value.isFinite() || value !in 0.0..1.0) throw IOException("Decision score outside [0, 1]")
            value
        }
    }
}
