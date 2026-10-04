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
 * A shape check over tables, columns and column types. [columns] reads the
 * type of each column that the store holds, for each table. Each module that
 * owns tables supplies its [required] set, so a store is checked for each
 * backend that uses it.
 *
 * [types] names the type that a column must have. A store from an earlier
 * schema can hold every name with another type. It then starts, and fails at
 * the first write. See `CHAT-xcmpudyb`.
 *
 * [store] names the store in the message, such as `Keyspace chat_long`.
 */
class ColumnShapeCheck(
    private val store: String,
    private val required: Map<String, Set<String>>,
    private val types: Map<String, Map<String, String>> = emptyMap(),
    private val columns: () -> Map<String, Map<String, String>>,
) : StoreShapeCheck {
    override fun check() {
        val have = columns()
        val names = (required.keys + types.keys).associateWith { table ->
            required[table].orEmpty() + types[table].orEmpty().keys
        }
        val missing = names.flatMap { (table, cols) ->
            val present = have[table]
            if (present == null) listOf(table) else (cols - present.keys).map { "$table.$it" }
        }
        val wrong = types.flatMap { (table, cols) ->
            cols.mapNotNull { (col, type) ->
                have[table]?.get(col)?.takeIf { it != type }?.let { "$table.$col is $it, required $type" }
            }
        }
        if (missing.isNotEmpty() || wrong.isNotEmpty()) throw com.demo.chat.domain.ChatException(
            "$store does not match the required schema." +
                (if (missing.isEmpty()) "" else " Missing: ${missing.joinToString()}.") +
                (if (wrong.isEmpty()) "" else " Wrong type: ${wrong.joinToString()}.") +
                " Recreate the store from keyspace-*.cql. This release has no migration."
        )
    }
}
