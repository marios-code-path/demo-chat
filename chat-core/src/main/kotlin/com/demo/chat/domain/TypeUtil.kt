package com.demo.chat.domain

interface TypeUtil<T> {
    fun compare(a: T, b: T): Int
    fun toString(t: T): String
    fun fromString(t: String): T
    fun assignFrom(t: Any): T
}

class LongUtil: TypeUtil<Long> {
    override fun compare(a: Long, b: Long): Int = a.compareTo(b)

    override fun toString(t: Long): String = t.toString()

    override fun fromString(t: String): Long = t.toLong()

    override fun assignFrom(t: Any): Long {
        return when(t) {
            is Long -> t.toLong()
            else -> 0L
        }
    }
}