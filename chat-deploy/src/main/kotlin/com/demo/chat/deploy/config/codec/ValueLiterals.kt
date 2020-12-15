package com.demo.chat.deploy.config.codec

interface ValueLiterals<V> {
    fun emptyValue(): V
    fun fromString(t: String): V
}