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

interface MessageKey {
    val id: UUID
    val roomId: UUID
    val timestamp: Instant
}

interface MessageTxtKey  : MessageKey {
    override val id: UUID
    open val userId: UUID
    override val roomId: UUID
    override val timestamp: Instant
}

interface MessageAlrtKey : MessageKey {
    override val id: UUID
    override val roomId: UUID
    override val timestamp: Instant
}

// Variances of Keys we want
open class MessageAlertKey(
        override val id: UUID,
        override val roomId: UUID,
        override val timestamp: Instant
) : MessageKey


open class MessageTextKey(
        override val id: UUID,
        open val userId: UUID,
        override val roomId: UUID,
        override val timestamp: Instant
) : MessageKey

open class ChatRoomTextMessage(
        override val key: MessageTextKey,
        override val value: String,
        override val visible: Boolean
) : Message<MessageTextKey, String>

data class ChatRoomInfoAlert(
        override val key: MessageAlertKey,
        override val value: RoomInfo,
        override val visible: Boolean)
    : Message<MessageAlertKey, RoomInfo>


data class ChatRoomLeaveAlert(
        override val key: MessageAlertKey,
        override val value: UUID,
        override val visible: Boolean)
    : Message<MessageAlertKey, UUID>

data class ChatRoomJoinAlert(
        override val key: MessageAlertKey,
        override val value: UUID,
        override val visible: Boolean)
    : Message<MessageAlertKey, UUID>