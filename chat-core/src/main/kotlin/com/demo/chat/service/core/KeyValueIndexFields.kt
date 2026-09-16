package com.demo.chat.service.core

import com.demo.chat.domain.ChatException

/**
 * Maps one key-value pair value to the fields that a key-value index stores.
 *
 * A [KeyValueStore] holds values of any type, so the index cannot know what
 * to index from the value alone. Every value type that needs an index
 * registers one of these.
 *
 * Both the lucene index and the cassandra index take the same instance, so
 * one value type indexes the same fields on every backend.
 */
fun interface KeyValueIndexFields {
    fun fieldsOf(value: Any): List<Pair<String, String>>
}

/**
 * One registration. A module contributes a bean of this type to make its own
 * value type indexable. The index collects every entry it finds.
 */
data class KeyValueIndexFieldsEntry(
    val type: Class<*>,
    val fields: KeyValueIndexFields,
)

/**
 * Selects the registered [KeyValueIndexFields] for the runtime class of a
 * value.
 *
 * An unregistered type throws rather than indexing nothing. A key-value index
 * that silently stored no fields would answer every later query with an empty
 * result, and an empty result cannot be told apart from a real miss.
 */
class TypedKeyValueIndexFields(entries: List<KeyValueIndexFieldsEntry>) : KeyValueIndexFields {
    private val byType: Map<Class<*>, KeyValueIndexFields> =
        entries.associate { entry -> entry.type to entry.fields }

    override fun fieldsOf(value: Any): List<Pair<String, String>> {
        val fields = byType[value.javaClass] ?: throw ChatException(
            if (byType.isEmpty()) {
                "No key value index fields are registered at all, so '${value.javaClass.name}' " +
                    "cannot be indexed. A module registers a value type with a " +
                    "KeyValueIndexFieldsEntry bean."
            } else {
                "No key value index fields for '${value.javaClass.name}'. " +
                    "Registered types: ${byType.keys.map { it.name }.sorted().joinToString(", ")}"
            }
        )
        return fields.fieldsOf(value)
    }
}
