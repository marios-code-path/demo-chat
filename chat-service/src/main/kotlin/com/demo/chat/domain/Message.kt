package com.demo.chat.domain

import java.time.Instant
import java.util.*

interface MessageKey {
    val id: UUID
    val userId: UUID
    val roomId: UUID
    val timestamp: Instant
}

interface Message<T : MessageKey> {
    val key: T
    val text: String
    val visible: Boolean
}