package com.demo.chat.domain

interface ValueLiterals<V> {
    fun emptyValue(): V
    fun fromString(t: String): V
}