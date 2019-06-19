package com.demo.chat.domain

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.time.Instant
import java.util.*

@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
@JsonSubTypes(JsonSubTypes.Type(TextMessage::class),
        JsonSubTypes.Type(InfoAlert::class))
interface Message<out K, out V> {
    val key: K
    val value: V
    val visible: Boolean
}

interface TextMessage : Message<TextMessageKey, String>

interface InfoAlert : Message<AlertMessageKey, RoomMetaData>

interface LeaveAlert : Message<AlertMessageKey, UUID>

interface JoinAlert : Message<AlertMessageKey, UUID>

interface PauseAlert : Message<AlertMessageKey, UUID>

interface MessageKey {
    val msgId: UUID
    val timestamp: Instant
}

interface TopicMessageKey : MessageKey {
    override val msgId: UUID
    val topicId: UUID
    override val timestamp: Instant
}

interface TextMessageKey : TopicMessageKey {
    val userId: UUID
}

interface AlertMessageKey : TopicMessageKey // {
//  private val ttl: Int
// }