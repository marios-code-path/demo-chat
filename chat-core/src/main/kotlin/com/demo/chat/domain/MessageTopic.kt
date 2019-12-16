package com.demo.chat.domain

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import java.util.*

@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
@JsonTypeName("Topic")
interface MessageTopic<K> : KeyDataPair<K, String> {
    companion object Factory {
        fun<K> create(key: Key<K>, name: String) = object : MessageTopic<K> {
            override val key: Key<K>
                get() = key
            override val data: String
                get() = name
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