package com.demo.chat.test

import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.fasterxml.jackson.annotation.JsonTypeName


// Only SimpleKey, EmptyKey and SimpleMessageKey implement Key. The test key
// classes that stood here are removed. See CHAT-avduuqwp.

@JsonTypeName("Alert")
data class TestAlert<T>(override val key: MessageKey<T>, override val data: Int) : Message<T, Int> {
    override val record = false
}

@JsonTypeName("Text")
data class TestTextMessage<T>(override val key: MessageKey<T>, override val data: String) : Message<T, String> {
    override val record = true
}