package com.demo.chat.convert

import com.fasterxml.jackson.dataformat.cbor.CBORFactory
import com.fasterxml.jackson.databind.ObjectMapper
import java.util.UUID
import java.time.Instant
import java.math.BigDecimal

/**
 * The rules that turn a JSON scalar into a domain value.
 *
 * **Two Jackson generations read the same wire, and they must decide the same
 * way.** The Jackson 2 codecs serve the stores of this repository, and the
 * Jackson 3 codec serves HTTP under Spring Boot 4. A node type switch has to
 * be written once per generation, because the node types differ, but the
 * decisions inside it do not. They live here.
 *
 * Each rule is the one the Jackson 2 converter has always applied. A change
 * here changes both generations at once, which is the point. See
 * CHAT-qwmjrixq.
 */
object NodeValueRules {

    /**
     * A whole number reads as a Long, and anything else as a Double.
     *
     * The caller supplies both readings, because a node type decides how to
     * produce them and this rule decides which one to keep.
     */
    fun number(asDouble: Double, asLong: () -> Long): Any =
        if (asDouble % 1 == 0.0) asLong() else asDouble

    /**
     * Text reads as a UUID when it parses as one, and as text otherwise.
     *
     * **A key can arrive as either.** The uuid key type writes a uuid string,
     * and every other value is text that must survive unchanged.
     */
    fun text(value: String): Any = try {
        UUID.fromString(value)
    } catch (e: Exception) {
        value
    }

    /**
     * Binary reads as a UUID when the CBOR bytes hold one.
     *
     * The fallback keeps the earlier behaviour exactly, including that it
     * answers the `toString` of the byte array rather than its content.
     */
    fun binary(bytes: ByteArray): Any {
        val mapper = ObjectMapper(CBORFactory())
        val cborData = mapper.writeValueAsBytes(bytes)

        return try {
            mapper.readValue(cborData, UUID::class.java)
        } catch (e: Exception) {
            cborData.toString()
        }
    }
}

/**
 * The rule that decides which kind of key a node holds.
 *
 * **A `Key` and a `MessageKey` share one wire shape**, and the presence of
 * `from` and `dest` is what separates them. Both Jackson generations apply
 * this rule, so a key decoded over HTTP and a key decoded from a store agree.
 *
 * **A key needs a root.** Each generation refuses a payload with no `root`, or
 * with a null `root`, and raises its own mapping exception with
 * [MISSING_ROOT]. A payload with `empty` set to true decodes to an empty key.
 * See `CHAT-avduuqwp`.
 */
object KeyAssembly {

    /** This function reads ISO timestamps, decimal epoch seconds, or whole numbers in the selected unit. */
    fun timestamp(value: String?, numeric: Boolean, milliseconds: Boolean = false): Instant? {
        if (value == null) return null
        if (!numeric) return Instant.parse(value)
        if (milliseconds) return Instant.ofEpochMilli(BigDecimal(value).longValueExact())
        val seconds = BigDecimal(value)
        return Instant.ofEpochSecond(seconds.toBigInteger().longValueExact(), seconds.remainder(BigDecimal.ONE).movePointRight(9).toLong())
    }

    const val MISSING_ROOT = "A key needs a root. The payload holds none."

    fun <T : Any> key(id: T, root: T, empty: Boolean, from: T?, dest: T?, timestamp: Instant? = null): com.demo.chat.domain.Key<T> =
        when {
            empty -> com.demo.chat.domain.Key.empty(id, root)
            from != null && dest != null -> com.demo.chat.domain.SimpleMessageKey(id, root, from, dest, timestamp ?: Instant.now())
            else -> com.demo.chat.domain.Key.of(id, root)
        }
}
