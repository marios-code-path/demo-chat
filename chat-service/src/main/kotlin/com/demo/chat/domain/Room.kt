package com.demo.chat.domain

import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.time.Instant
import java.util.*

// Kludge Log: Cassandra requires nullable Set ( when returns with empty set )
@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
interface Room<out T> {
    val key: T
    val members: Set<UUID>?
    val timestamp: Instant

}

interface RoomKey {
    val roomId: UUID
    val name: String
}

data class RoomInfo(
        val activeMembers: Int,
        val totalMessages: Int
)

data class RoomMember(
        val uid: UUID,
        val handle: String
)
data class RoomMemberships(
        val members: Set<RoomMember>
)