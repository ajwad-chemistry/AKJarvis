package com.akjarvis.app.data.web

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** Small JSON navigation helpers so every API parser stays one-liner clean. */
internal fun JsonElement?.obj(key: String): JsonElement? =
    (this as? JsonObject)?.get(key)

internal fun JsonElement?.arr(key: String? = null): JsonArray? = when {
    this == null -> null
    key != null -> (this as? JsonObject)?.get(key) as? JsonArray
    else -> this as? JsonArray
}

internal fun JsonElement?.str(key: String? = null): String? = when {
    this == null -> null
    key != null -> (this as? JsonObject)?.get(key)?.let { (it as? JsonPrimitive)?.content }
    else -> (this as? JsonPrimitive)?.content
}

internal fun JsonElement?.int(key: String? = null): Int? =
    str(key)?.toIntOrNull() ?: str(key)?.toDoubleOrNull()?.toInt()

internal fun JsonElement?.dbl(key: String? = null): Double? = str(key)?.toDoubleOrNull()

internal fun JsonElement?.at(index: Int): JsonElement? = (this as? JsonArray)?.getOrNull(index)

/**
 * Shared HTTP + JSON plumbing for every free, key-less web API the assistant
 * uses (weather, prayer times, scores, currency, knowledge, fun tools...).
 * All endpoints are HTTPS. No key, no account, no tracking.
 */
object Http {

    val json = Json { ignoreUnknownKeys = true; isLenient = true }

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    suspend fun getJson(url: String): JsonElement? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "AKJarvis/1.2 (personal assistant)")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                json.parseToJsonElement(response.body?.string() ?: return@use null)
            }
        }.getOrNull()
    }

    suspend fun postJson(url: String, body: String, headers: Map<String, String> = emptyMap()): JsonElement? =
        withContext(Dispatchers.IO) {
            runCatching {
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val request = Request.Builder()
                    .url(url)
                    .post(body.toRequestBody(mediaType))
                    .header("User-Agent", "AKJarvis/1.3 (personal assistant)")
                    .apply { headers.forEach { (k, v) -> header(k, v) } }
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    json.parseToJsonElement(response.body?.string() ?: return@use null)
                }
            }.getOrNull()
        }

    suspend fun getText(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "AKJarvis/1.2 (personal assistant)")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                response.body?.string()
            }
        }.getOrNull()
    }
}
