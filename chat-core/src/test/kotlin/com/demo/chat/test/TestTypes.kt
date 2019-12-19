package com.demo.chat.test

import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.domain.TextMessage
import com.demo.chat.domain.UserMessageKey
import com.fasterxml.jackson.annotation.JsonTypeName
import java.time.Instant
import java.util.*


@JsonTypeName("AlertKey")
data class TestAlertKey(override val dest: UUID) : MessageKey<UUID> {
    override val id: UUID = UUID(0, 0)
    override val timestamp = Instant.now()
}

@JsonTypeName("Alert")
data class TestAlert(override val key: TestAlertKey, override val data: Int) : Message<UUID, Int> {
    override val visible = false
}

@JsonTypeName("TextKey")
data class TestMessageKey(override val id: UUID, override val dest: UUID, override val userId: UUID) : UserMessageKey<UUID> {
    override val timestamp = Instant.now()
}

@JsonTypeName("Text")
data class TestTextMessage(override val key: TestMessageKey, override val data: String) : TextMessage<UUID> {
    override val visible = true
}
