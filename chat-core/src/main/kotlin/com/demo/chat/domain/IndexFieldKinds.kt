package com.demo.chat.domain

import com.demo.chat.service.core.MembershipIndexService
import com.demo.chat.service.core.MessageIndexService
import com.demo.chat.service.core.TopicIndexService
import com.demo.chat.service.core.UserIndexService
import com.demo.chat.service.security.AuthMetaIndex
import java.text.Normalizer
import java.util.Locale

/** How one index field matches a lookup value. */
enum class IndexMatch {
    /** The analyzer splits the value. A lookup parses its text with the analyzer. */
    TEXT,

    /** A lookup matches the whole value. */
    EXACT,

    /** A lookup matches the whole value. Case is ignored. */
    EXACT_IGNORE_CASE,
}

/**
 * Declares the field kinds of one index. The writer and lookup use the same
 * normalization. See `CHAT-hajmhslp`.
 *
 * [EXACT_PREFIX] marks a Lucene `StringField`. A term query reads that field.
 * Other fields use `TextField` and `QueryParser`. The field name carries this
 * rule through `IndexSearchRequest` without a new wire property.
 */
class IndexFieldKinds(
    private val kinds: Map<String, IndexMatch>,
    private val defaultKind: IndexMatch? = null,
) {

    /** The stored field name for [field] under [match]. A text name stays plain. */
    fun storedName(match: IndexMatch, field: String): String =
        if (match == IndexMatch.TEXT) field else EXACT_PREFIX + field

    /** Normalize [value] for both the stored field and its lookup. */
    fun storedValue(match: IndexMatch, value: String): String = when (match) {
        IndexMatch.TEXT -> value
        IndexMatch.EXACT -> normalize(value)
        IndexMatch.EXACT_IGNORE_CASE -> normalize(value).lowercase(Locale.ROOT)
    }

    /** One encoder field, with its kind applied to the name and to the value. */
    fun field(field: String, value: String): Pair<String, String> {
        val match = declared(field)
        return Pair(storedName(match, field), storedValue(match, value))
    }

    /** The request for one lookup on a declared field of this index. */
    fun query(field: String, value: String, limit: Int = DEFAULT_LIMIT): IndexSearchRequest =
        field(field, value).let { IndexSearchRequest(it.first, it.second, limit) }

    /**
     * Resolve the kind from [name]. Reject a name that disagrees with its
     * declaration. A silent miss could hide this error. See `CHAT-sgdtqhof`.
     */
    fun matchOf(name: String): IndexMatch {
        if (name.startsWith(EXACT_PREFIX)) {
            val plain = name.removePrefix(EXACT_PREFIX)
            val match = declared(plain)
            if (match == IndexMatch.TEXT) {
                throw ChatException("Index field '$plain' matches text. Query it by the plain name '$plain'.")
            }
            return match
        }
        val match = declared(name)
        if (match != IndexMatch.TEXT) {
            throw ChatException("Index field '$name' matches a whole value. Query it as '$EXACT_PREFIX$name'.")
        }
        return match
    }

    private fun declared(field: String): IndexMatch =
        kinds[field] ?: defaultKind ?: throw ChatException(
            "This index has no field named '$field'. It declares: ${kinds.keys.sorted().joinToString(", ")}."
        )

    companion object {
        /** The field-name prefix that marks a whole-value field. */
        const val EXACT_PREFIX = "_x_"

        const val DEFAULT_LIMIT = 100

        /** The one normalization. The index side and the lookup side both call it. */
        fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFC)

        /**
         * The handle field ignores case. A handle lookup must find `Admin` when
         * the caller writes `admin`.
         */
        val USER = IndexFieldKinds(
            mapOf(
                "key" to IndexMatch.EXACT,
                UserIndexService.HANDLE to IndexMatch.EXACT_IGNORE_CASE,
                UserIndexService.NAME to IndexMatch.EXACT,
            )
        )

        val TOPIC = IndexFieldKinds(
            mapOf(
                "key" to IndexMatch.EXACT,
                TopicIndexService.NAME to IndexMatch.EXACT,
            )
        )

        /** Message data is the only analyzed field supplied by an encoder. */
        val MESSAGE = IndexFieldKinds(
            mapOf(
                MessageIndexService.ID to IndexMatch.EXACT,
                MessageIndexService.DATA to IndexMatch.TEXT,
                MessageIndexService.TOPIC to IndexMatch.EXACT,
                MessageIndexService.USER to IndexMatch.EXACT,
            )
        )

        val MEMBERSHIP = IndexFieldKinds(
            mapOf(
                "key" to IndexMatch.EXACT,
                MembershipIndexService.MEMBER to IndexMatch.EXACT,
                MembershipIndexService.MEMBEROF to IndexMatch.EXACT,
                MembershipIndexService.MEMBER_PAIR to IndexMatch.EXACT,
            )
        )

        val AUTH = IndexFieldKinds(
            mapOf(
                AuthMetaIndex.PRINCIPAL to IndexMatch.EXACT,
                AuthMetaIndex.TARGET to IndexMatch.EXACT,
                AuthMetaIndex.ID to IndexMatch.EXACT,
            )
        )

        /**
         * A key-value entry declares its fields at runtime, through a
         * `KeyValueIndexFieldsEntry` bean. Every field is exact, because the
         * registration names a value and a lookup asks for that value.
         */
        val KEY_VALUE = IndexFieldKinds(emptyMap(), IndexMatch.EXACT)
    }
}
