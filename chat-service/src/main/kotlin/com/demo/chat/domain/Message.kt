package com.demo.chat.domain

import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.time.Instant
import java.util.*

@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
interface Message<out K, out V> {
    val key: K
    val value: V
    val visible: Boolean
}

interface TextMessage : Message<TextMessageKey, String>

interface InfoAlert : Message<AlertMessageKey, RoomInfo>

interface LeaveAlert : Message<AlertMessageKey, UUID>

interface JoinAlert : Message<AlertMessageKey, UUID>

interface ClosingAlert : Message<ClosingKey, UUID>

interface MessageKey {
    val id: UUID
    val roomId: UUID
    val timestamp: Instant
}

interface TextMessageKey : MessageKey {
    val userId: UUID
}

interface AlertMessageKey : MessageKey

interface ClosingKey : MessageKey
