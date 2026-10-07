package com.demo.chat.index.lucene.storage

import com.demo.chat.domain.ChatException
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.StoredField
import org.apache.lucene.document.StringField
import org.apache.lucene.document.TextField

/** The field names that the index writes itself. */
object IndexFields {
    /** The key text that a query returns. Stored and analyzed. */
    const val STORED_KEY = "key"

    /** The exact key. Not analyzed. Replacement and lookup match it. */
    const val EXACT_KEY = "_key"

    /** The canonical entry bytes. Stored, not indexed. */
    const val ENTRY = "_enc"

    val RESERVED = setOf(EXACT_KEY, ENTRY)
}

/**
 * One encoded entry. The document and the bytes come from one field list, so
 * the bytes always describe what Lucene indexed.
 */
class IndexEntry(val keyText: String, val bytes: ByteArray, val document: Document) {
    companion object {
        fun of(keyText: String, fields: List<Pair<String, String>>): IndexEntry {
            requireNoReservedField(fields)
            val bytes = EntryEncoding.encode(keyText, fields)
            val document = Document().apply {
                fields.forEach { (name, value) -> add(Field(name, value, TextField.TYPE_NOT_STORED)) }
                add(Field(IndexFields.STORED_KEY, keyText, TextField.TYPE_STORED))
                add(StringField(IndexFields.EXACT_KEY, keyText, Field.Store.NO))
                add(StoredField(IndexFields.ENTRY, bytes))
            }
            return IndexEntry(keyText, bytes, document)
        }

        /**
         * Rejects an encoded field that carries a reserved name.
         *
         * Lucene refuses a document that gives one name two index options. The
         * check runs before any writer change, so a refusal cannot lose an entry.
         */
        fun requireNoReservedField(fields: List<Pair<String, String>>) {
            fields.firstOrNull { it.first in IndexFields.RESERVED }?.let { field ->
                throw ChatException(
                    "An index field cannot use the name '${field.first}'. The index writes that field itself."
                )
            }
        }
    }
}
