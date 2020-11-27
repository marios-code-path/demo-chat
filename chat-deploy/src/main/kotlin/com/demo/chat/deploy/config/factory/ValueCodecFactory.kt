package com.demo.chat.deploy.config.factory

interface ValueCodecFactory<V> {
    fun emptyValue(): V
    //fun fromString(t: String): V
}