package com.demo.chat.domain

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import java.time.Instant
import java.util.*

// Kludge Log: Cassandra requires nullable Set ( when returns with empty set )
@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
interface Topic<out K> {
    val key: K
    val members: Set<UUID>?
    val timestamp: Instant
}

@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
interface Room : Topic<RoomKey>

interface RoomKey {
    val roomId: UUID
    val name: String
}

@JsonTypeName("RoomMeta")
data class RoomMetaData(
        val activeMembers: Int,
        val totalMessages: Int
)

@JsonTypeName("RoomMember")
data class RoomMember(
        val uid: UUID,
        val handle: String
)

// For later , when we disconnect room.memberSet into it's own Column
@JsonTypeName("RoomMemberships")
data class RoomMemberships(
        val members: Set<RoomMember>
)