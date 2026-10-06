package com.rodrigos01.aipodcasts.data.api

import com.squareup.moshi.Moshi
import retrofit2.HttpException

private val errorAdapter = Moshi.Builder().build().adapter(Map::class.java)

/**
 * A readable message for an API failure. For an HTTP error it reads the backend's JSON body
 * (`message`, plus the first few validation `details` as "path: reason") instead of just "HTTP 400".
 */
fun Throwable.userMessage(): String? {
    if (this !is HttpException) return localizedMessage ?: message
    val body = runCatching { response()?.errorBody()?.string() }.getOrNull()
    return parseErrorBody(body) ?: message()
}

internal fun parseErrorBody(body: String?): String? {
    if (body.isNullOrBlank()) return null
    val json = runCatching { errorAdapter.fromJson(body) }.getOrNull() ?: return null
    val message = json["message"] as? String
    val details = (json["details"] as? List<*>)
        ?.filterIsInstance<Map<*, *>>()
        ?.take(3)
        ?.mapNotNull { issue ->
            val reason = issue["message"] as? String ?: return@mapNotNull null
            val path = (issue["path"] as? List<*>)
                // Moshi reads JSON numbers as Double, so an index 0 would print as "0.0".
                ?.joinToString(".") { if (it is Double && it % 1.0 == 0.0) it.toInt().toString() else it.toString() }?.takeIf { it.isNotEmpty() }
            if (path != null) "$path: $reason" else reason
        }
        .orEmpty()
    return listOfNotNull(message, details.takeIf { it.isNotEmpty() }?.joinToString("; ")).joinToString(" — ")
        .ifBlank { null }
}
