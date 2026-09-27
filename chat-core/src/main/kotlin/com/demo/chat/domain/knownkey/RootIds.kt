package com.demo.chat.domain.knownkey

import com.demo.chat.domain.ChatException
import com.demo.chat.domain.TypeUtil

/**
 * The strict parser for a root or identity id that arrives as text. See
 * `CHAT-avduuqwp`.
 *
 * `TypeUtil.fromString` is permissive. The Long parser returns zero for
 * malformed or overflowing text, so two distinct roots could both become zero.
 * This parser accepts a value only when it writes back to the same text. It
 * also refuses the empty value of the key type, because that value marks an
 * empty key.
 */
object RootIds {

    /** This method parses [text] for the root that [field] names, or throws a `ChatException`. */
    fun <T> parse(typeUtil: TypeUtil<T>, text: String?, field: String): T {
        if (text.isNullOrBlank()) throw ChatException("The root id for $field is missing.")
        val value = try {
            typeUtil.fromString(text)
        } catch (e: IllegalArgumentException) {
            throw ChatException("The root id for $field is not valid: '$text'.")
        }
        if (typeUtil.toString(value) != text) throw ChatException("The root id for $field is not valid: '$text'.")
        if (value == typeUtil.empty()) throw ChatException("The root id for $field is the empty value: '$text'.")
        return value
    }
}
