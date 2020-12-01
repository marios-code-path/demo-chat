package com.demo.chat.deploy.config.core

interface ValueCodecFactory<V> {
    fun emptyValue(): V
    //fun fromString(t: String): V
}