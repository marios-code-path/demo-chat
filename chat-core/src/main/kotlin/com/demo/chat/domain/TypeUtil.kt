package com.demo.chat.domain

import org.springframework.core.ParameterizedTypeReference
import java.lang.NumberFormatException
import java.util.*

// Path: chat-core/src/main/kotlin/com/demo/chat/domain/TypeUtil.kt
interface TypeUtil<T> {
    fun compare(a: T, b: T): Int
    fun toString(t: T): String
    fun fromString(t: String): T
    fun assignFrom(t: Any): T
    fun parameterizedType(): ParameterizedTypeReference<T>
    fun empty(): T

    companion object LongUtil : TypeUtil<Long> {
        override fun compare(a: Long, b: Long): Int = a.compareTo(b)

        override fun toString(t: Long): String = t.toString()

        override fun fromString(t: String): Long = try {
            java.lang.Long.parseLong(t)
        } catch (e: NumberFormatException) {
            0L
        }

        override fun assignFrom(t: Any): Long {
            return when (t) {
                is String -> java.lang.Long.parseLong(t)
                is Long -> t.toLong()
                else -> 0L
            }
        }

        override fun parameterizedType(): ParameterizedTypeReference<Long> =
            ParameterizedTypeReference.forType(Long::class.java)

        override fun empty(): Long = 0L
    }
}

class LongUtil : TypeUtil<Long> {
    override fun compare(a: Long, b: Long): Int = a.compareTo(b)

    override fun toString(t: Long): String = t.toString()

    override fun fromString(t: String): Long = try {
        java.lang.Long.parseLong(t)
    } catch (e: NumberFormatException) {
        0L
    }

    override fun assignFrom(t: Any): Long {
        return when (t) {
            is String -> java.lang.Long.parseLong(t)
            is Long -> t.toLong()
            else -> 0L
        }
    }

    override fun parameterizedType(): ParameterizedTypeReference<Long> =
        ParameterizedTypeReference.forType(Long::class.java)

    override fun empty(): Long = 0L
}

class UUIDUtil : TypeUtil<UUID> {
    override fun compare(a: UUID, b: UUID): Int = a.compareTo(b)

    override fun toString(t: UUID): String = t.toString()

    override fun fromString(t: String): UUID = UUID.fromString(t)

    override fun assignFrom(t: Any): UUID {
        return when (t) {
            is String -> fromString(t)
            is Long -> UUID(t, 0)
            else -> UUID(0, 0)
        }
    }

    override fun parameterizedType(): ParameterizedTypeReference<UUID> =
        ParameterizedTypeReference.forType(UUID::class.java)

    override fun empty(): UUID = UUID(0, 0)
}

class StringUtil: TypeUtil<String> {
    override fun compare(a: String, b: String): Int = a.compareTo(b)

    override fun toString(t: String): String = t

    override fun fromString(t: String): String = t

    override fun assignFrom(t: Any): String = when (t) {
        is String -> fromString(t)
        else -> t.toString()
    }

    override fun parameterizedType(): ParameterizedTypeReference<String> =
        ParameterizedTypeReference.forType(String::class.java)

    override fun empty(): String = ""
}