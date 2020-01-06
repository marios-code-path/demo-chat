package com.demo.chatevents.topic

import com.demo.chat.domain.*
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import java.time.Instant
import java.util.*


@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
@JsonTypeName("TopicData")
@Deprecated("NOT A REAL VALUE ANYMORE")
class TopicData<T, V>(var state: Message<T, out V>)

@JsonTypeName("ChatMessage")
open class ChatMessage<T>(
        override val key: ChatMessageKey<T>,
        override val data: String,
        override val record: Boolean
) : TextMessage<T>

data class ChatMessageKey<T>(
        override val id: T,
        override val userId: T,
        override val dest: T,
        override val timestamp: Instant
) : UserMessageKey<T>