package com.demo.chat.domain

import java.time.Instant
import java.util.*

interface Room<T : RoomKey> {
    val key: T
    val members: Set<UUID>?
    val timestamp: Instant

}

interface RoomKey {
    val roomId: UUID
    val name: String
}