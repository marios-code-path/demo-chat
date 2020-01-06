package com.demo.chat.test

import com.demo.chat.domain.*
import com.fasterxml.jackson.annotation.JsonTypeName
import java.time.Instant


@JsonTypeName("Key")
data class TestKey<T>( override val id: T) : Key<T>

@JsonTypeName("AlertKey")
data class TestAlertKey<T>(override val id: T, override val dest: T) : MessageKey<T> {
    override val timestamp = Instant.now()
}

@JsonTypeName("Alert")
data class TestAlert<T>(override val key: TestAlertKey<T>, override val data: Int) : Message<T, Int> {
    override val record = false
}

@JsonTypeName("MessageKey")
data class TestMessageKey<T>(override val id: T, override val dest: T, override val userId: T) : UserMessageKey<T> {
    override val timestamp = Instant.now()
}

@JsonTypeName("Text")
data class TestTextMessage<T>(override val key: TestMessageKey<T>, override val data: String) : TextMessage<T> {
    override val record = true
}

@JsonTypeName("User")
data class TestUser<T>(override val key: TestKey<T>, override val name: String, override val handle: String, override val imageUri: String, override val timestamp: Instant) : User<T>

@JsonTypeName("Topic")
data class TestTopic<T>(override val key: TestKey<T>, override val data: String) : MessageTopic<T>
