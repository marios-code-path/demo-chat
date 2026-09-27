package com.demo.chat.domain

/**
 * A key id that a caller sent does not convert exactly to the key type. See
 * `CHAT-avduuqwp`, T4 review.
 *
 * It is an input error, and no registry read runs for it. A fraction, a value
 * outside the key type, and an unsupported shape each raise it. An id that
 * converts and that the registry does not hold is not this error.
 */
class KeyInputException(message: String) : ChatException(message)

/**
 * The exact conversions of a caller id. Each one refuses a value that it would
 * otherwise round, truncate, wrap, or guess.
 */
object ExactIds {
    private val integer = Regex("-?[0-9]+")
    private val canonicalUuid = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

    fun long(t: Any): Long = when (t) {
        is Long -> t
        is Int -> t.toLong()
        is Short -> t.toLong()
        is Byte -> t.toLong()
        is java.math.BigInteger ->
            if (t.bitLength() < 64) t.toLong() else throw KeyInputException("The key id $t is outside the Long range.")
        is String ->
            if (integer.matches(t)) t.toLongOrNull() ?: throw KeyInputException("The key id '$t' is outside the Long range.")
            else throw KeyInputException("The key id '$t' is not an integer.")
        is Number -> throw KeyInputException("The key id $t is not an integer.")
        else -> throw KeyInputException("A key id cannot be a ${t.javaClass.simpleName}.")
    }

    fun uuid(t: Any): java.util.UUID = when (t) {
        is java.util.UUID -> t
        is String ->
            if (canonicalUuid.matches(t)) java.util.UUID.fromString(t)
            else throw KeyInputException("The key id '$t' is not a canonical UUID.")
        else -> throw KeyInputException("A key id cannot be a ${t.javaClass.simpleName}.")
    }

    fun string(t: Any): String = t as? String ?: throw KeyInputException("A key id cannot be a ${t.javaClass.simpleName}.")
}
