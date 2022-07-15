package com.demo.chat.domain

import org.springframework.core.ParameterizedTypeReference
import java.util.*

interface TypeUtil<T> {
    fun compare(a: T, b: T): Int
    fun toString(t: T): String
    fun fromString(t: String): T
    fun assignFrom(t: Any): T
    fun parameterizedType(): ParameterizedTypeReference<T>

    companion object LongUtil : TypeUtil<Long> {
        override fun compare(a: Long, b: Long): Int = a.compareTo(b)

        override fun toString(t: Long): String = t.toString()

        override fun fromString(t: String): Long = t.toLong()

        override fun assignFrom(t: Any): Long {
            return when (t) {
                is Long -> t.toLong()
                else -> 0L
            }
        }

        override fun parameterizedType(): ParameterizedTypeReference<Long> =
            ParameterizedTypeReference.forType(Long::class.java)
    }

}

class UUIDUtil : TypeUtil<UUID> {
    override fun compare(a: UUID, b: UUID): Int = a.compareTo(b)

    override fun toString(t: UUID): String = t.toString()

    override fun fromString(t: String): UUID = UUID.fromString(t)

    override fun assignFrom(t: Any): UUID {
        return when (t) {
            is Long -> UUID(t, 0)
            else -> UUID(0, 0)
        }
    }

    override fun parameterizedType(): ParameterizedTypeReference<UUID> =
        ParameterizedTypeReference.forType(UUID::class.java)
}