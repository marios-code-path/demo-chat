package com.demo.chat.deploy.config.core

interface ValueLiterals<V> {
    fun emptyValue(): V
    fun fromString(t: String): V
}