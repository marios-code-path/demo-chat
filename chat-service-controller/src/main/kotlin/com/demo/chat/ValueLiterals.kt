package com.demo.chat

interface ValueLiterals<V> {
    fun emptyValue(): V
    fun fromString(t: String): V
}