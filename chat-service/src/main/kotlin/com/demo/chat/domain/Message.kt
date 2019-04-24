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

interface TextMessage : Message<MessageTextKey, String>

interface InfoAlert : Message<MessageAlertKey, RoomInfo>

interface LeaveAlert : Message<MessageAlertKey, UUID>

interface JoinAlert : Message<MessageAlertKey, UUID>

interface MessageKey {
    val id: UUID
    val roomId: UUID
    val timestamp: Instant
}

interface MessageTextKey : MessageKey {
    val userId: UUID
}

interface MessageAlertKey : MessageKey

