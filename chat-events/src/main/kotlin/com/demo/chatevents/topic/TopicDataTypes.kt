package com.demo.chatevents.topic

import com.demo.chat.domain.*
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import java.time.Instant
import java.util.*


object EmptyMessage : ChatMessage(ChatMessageKey(UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        Instant.now()),
        "",
        false)

@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
@JsonTypeName("TopicData")
class TopicData(var state: Message<TopicMessageKey, Any>) {
    constructor() : this(EmptyMessage)
}

@JsonTypeName("ChatMessage")
open class ChatMessage(
        override val key: ChatMessageKey,
        override val value: String,
        override val visible: Boolean
) : TextMessage

data class ChatMessageKey(
        override val msgId: UUID,
        override val userId: UUID,
        override val topicId: UUID,
        override val timestamp: Instant
) : TextMessageKey

@JsonTypeName("JoinAlert")
data class JoinAlertMessage(override val key: JoinAlertMessageKey,
                            override val value: UUID,
                            override val visible: Boolean
) : JoinAlert

data class JoinAlertMessageKey(override val msgId: UUID,
                               override val topicId: UUID,
                               override val timestamp: Instant
) : AlertMessageKey