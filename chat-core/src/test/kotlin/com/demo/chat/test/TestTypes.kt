package com.demo.chat.test

import com.demo.chat.domain.Key
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.fasterxml.jackson.annotation.JsonTypeName
import java.time.Instant


@JsonTypeName("Key")
data class TestKey<T>(override val id: T, override val empty: Boolean = false) : Key<T>

@JsonTypeName("AlertKey")
data class TestAlertKey<T>(override val id: T, override val dest: T, override val from: T, override val empty: Boolean = false) : MessageKey<T> {
    override val timestamp = Instant.now()
}

@JsonTypeName("Alert")
data class TestAlert<T>(override val key: TestAlertKey<T>, override val data: Int) : Message<T, Int> {
    override val record = false
}

@JsonTypeName("MessageKey")
data class TestMessageKey<T>(override val id: T, override val dest: T, override val from: T, override val empty: Boolean = false) : MessageKey<T> {
    override val timestamp = Instant.now()
}

@JsonTypeName("Text")
data class TestTextMessage<T>(override val key: TestMessageKey<T>, override val data: String) : Message<T, String> {
    override val record = true
}