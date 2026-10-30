package com.rodrigos01.aipodcasts.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import okio.Buffer
import java.lang.reflect.Type
import kotlin.reflect.full.primaryConstructor

/**
 * A model that round-trips JSON fields the client doesn't know about. Wizard objects are echoed
 * back to the server (revise / create), so a field the server adds must survive without a
 * client change: [UnknownFieldsAdapterFactory] stores every unrecognised property in [extras]
 * when reading and writes them back when serializing.
 *
 * Implementations declare `@Transient val extras: Map<String, Any?> = emptyMap()` as their last
 * constructor parameter (so Moshi skips it) and implement [withExtras] as `copy(extras = extras)`.
 * A known property always wins over an extra of the same name, so edits are never overwritten.
 */
interface UnknownFieldsHolder {
    val extras: Map<String, Any?>
    fun withExtras(extras: Map<String, Any?>): UnknownFieldsHolder
}

class UnknownFieldsAdapterFactory : JsonAdapter.Factory {
    override fun create(type: Type, annotations: Set<Annotation>, moshi: Moshi): JsonAdapter<*>? {
        if (annotations.isNotEmpty()) return null
        val raw = Types.getRawType(type)
        if (!UnknownFieldsHolder::class.java.isAssignableFrom(raw)) return null

        val knownNames = raw.kotlin.primaryConstructor!!.parameters
            .filter { it.name != EXTRAS }
            .mapNotNull { param -> param.annotations.filterIsInstance<Json>().firstOrNull()?.name ?: param.name }
            .toSet()
        val delegate = moshi.nextAdapter<UnknownFieldsHolder>(this, type, annotations)
        return Adapter(delegate, knownNames)
    }

    private class Adapter(
        private val delegate: JsonAdapter<UnknownFieldsHolder>,
        private val knownNames: Set<String>
    ) : JsonAdapter<UnknownFieldsHolder>() {

        override fun fromJson(reader: JsonReader): UnknownFieldsHolder? {
            if (reader.peek() == JsonReader.Token.NULL) return reader.nextNull()
            val tree = reader.readJsonValue() as Map<*, *>
            val value = delegate.fromJsonValue(tree) ?: return null
            val extras = LinkedHashMap<String, Any?>()
            for ((key, v) in tree) if (key is String && key !in knownNames) extras[key] = v
            return if (extras.isEmpty()) value else value.withExtras(extras)
        }

        override fun toJson(writer: JsonWriter, value: UnknownFieldsHolder?) {
            if (value == null) {
                writer.nullValue()
                return
            }
            if (value.extras.isEmpty()) {
                delegate.toJson(writer, value)
                return
            }
            // Serialize through a buffer (not toJsonValue) so the delegate omits nulls like it always has.
            val buffer = Buffer()
            delegate.toJson(JsonWriter.of(buffer), value)
            val known = JsonReader.of(buffer).readJsonValue() as Map<*, *>
            val merged = LinkedHashMap<String, Any?>(value.extras)
            for ((key, v) in known) merged[key as String] = v
            writer.jsonValue(merged)
        }
    }

    private companion object {
        const val EXTRAS = "extras"
    }
}
