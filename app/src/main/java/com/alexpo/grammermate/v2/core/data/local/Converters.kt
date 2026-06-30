package com.alexpo.grammermate.v2.core.data.local

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Room TypeConverters для коллекций, сериализуемых в JSON-строку колонки.
 *
 * Используется [kotlinx.serialization.json.Json] (а не org.json): типобезопасно,
 * быстрее и единообразно с остальной сериализацией приложения. Покрывает:
 *  - [List]<[String]> ↔ JSON String (включая pool-карточек / CardId-списки),
 *  - [Set]<[String]> ↔ JSON String,
 *  - [Map]<[String], [Int]> ↔ JSON String.
 *
 * `null` / пустая коллекция нормализуются к `"[]"` (или `"{}"`), чтобы колонки
 * оставались NOT NULL-совместимыми и предсказуемыми при чтении.
 *
 * @see <a href="../../../../../../docs/v2-architecture/ROOM_SCHEMA.md">ROOM_SCHEMA.md</a>
 */
class Converters {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val listSerializer = ListSerializer(String.serializer())
    private val setSerializer = SetSerializer(String.serializer())
    private val mapSerializer = MapSerializer(String.serializer(), Int.serializer())

    // ───────────────────────── List<String> ─────────────────────────

    @TypeConverter
    fun listToJson(value: List<String>?): String =
        value?.let { json.encodeToString(listSerializer, it) } ?: "[]"

    @TypeConverter
    fun jsonToList(value: String?): List<String> =
        value?.takeUnless { it.isBlank() }
            ?.let { json.decodeFromString(listSerializer, it) }
            ?: emptyList()

    // ────────────────────────── Set<String> ──────────────────────────

    @TypeConverter
    fun setToJson(value: Set<String>?): String =
        value?.let { json.encodeToString(setSerializer, it) } ?: "[]"

    @TypeConverter
    fun jsonToSet(value: String?): Set<String> =
        value?.takeUnless { it.isBlank() }
            ?.let { json.decodeFromString(setSerializer, it) }
            ?: emptySet()

    // ───────────────────────── Map<String, Int> ─────────────────────

    @TypeConverter
    fun mapToJson(value: Map<String, Int>?): String =
        value?.let { json.encodeToString(mapSerializer, it) } ?: "{}"

    @TypeConverter
    fun jsonToMap(value: String?): Map<String, Int> =
        value?.takeUnless { it.isBlank() }
            ?.let { json.decodeFromString(mapSerializer, it) }
            ?: emptyMap()
}
