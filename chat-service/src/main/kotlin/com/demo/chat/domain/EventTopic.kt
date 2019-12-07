package com.demo.chat.domain

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import java.util.*

// Must name become a permenant member of Room ?
// Kludge Log: Cassandra requires nullable Set ( when returns with empty set )
@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
interface Topic<out K, N> {
    val key: K
    val name: N

    companion object Factory {
        fun create(key: TopicKey, name: String) = object : Topic<UUIDKey, String> {
            override val key: TopicKey
                get() = key
            override val name: String
                get() = name
        }
    }
}

@JsonTypeName("Topic") // should be 'Topic'
interface EventTopic : Topic<UUIDKey, String> {
    companion object Factory {
        fun create(key: TopicKey, name: String) = object : EventTopic {
            override val key: TopicKey
                get() = key
            override val name: String
                get() = name
        }
    }
}

interface TopicKey : UUIDKey {
    companion object Factory {
        fun create(roomId: UUID) = object : TopicKey {
            override val id: UUID
                get() = roomId
        }
    }
}

@JsonTypeName("TopicMeta")
@Deprecated("Topic Metadata no longer associated at Data-Store level")
data class TopicMetaData(
        val activeMembers: Int,
        val totalMessages: Int
)

@JsonTypeName("TopicMember")
data class TopicMember(
        val uid: UUID,
        val handle: String,
        val imgUri: String
)

@JsonTypeName("RoomMemberships")
data class TopicMemberships(
        val members: Set<TopicMember>
)