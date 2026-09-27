package com.demo.chat.service.core

/**
 * The start check of a store shape. See `CHAT-avduuqwp`, T7.
 *
 * A store written before the key root work lacks elements that this release
 * reads. This release has no migration. So [check] fails with a message that
 * names each missing element and the recreation, before the root keys load.
 */
fun interface StoreShapeCheck {
    /** Returns when the store has the complete shape. Throws `ChatException` otherwise. */
    fun check()
}

/**
 * A shape check over tables and columns. [columns] reads the columns that the
 * store holds, for each table. Each module that owns tables supplies its
 * [required] set, so a store is checked for each backend that uses it.
 *
 * [store] names the store in the message, such as `Keyspace chat_long`.
 */
class ColumnShapeCheck(
    private val store: String,
    private val required: Map<String, Set<String>>,
    private val columns: () -> Map<String, Set<String>>,
) : StoreShapeCheck {
    override fun check() {
        val have = columns()
        val missing = required.flatMap { (table, cols) ->
            val present = have[table]
            if (present == null) listOf(table) else (cols - present).map { "$table.$it" }
        }
        if (missing.isNotEmpty()) throw com.demo.chat.domain.ChatException(
            "$store does not match the required schema. Missing: ${missing.joinToString()}. " +
                "Recreate the store from keyspace-*.cql. This release has no migration."
        )
    }
}
