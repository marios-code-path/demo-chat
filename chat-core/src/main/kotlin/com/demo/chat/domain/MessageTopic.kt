package com.demo.chat.domain

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import java.util.*

@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
@JsonTypeName("topic")
interface MessageTopic<T> : KeyDataPair<T, String> {
    companion object Factory {
        fun <T> create(key: Key<T>, name: String) = object : MessageTopic<T> {
            override val key: Key<T>
                get() = key
            override val data: String
                get() = name
        }
    }
}

@JsonTypeName("topicMeta")
@Deprecated("Topic Metadata no longer associated at Data-Store level")
data class TopicMetaData(
        val activeMembers: Int,
        val totalMessages: Int
)

@JsonTypeName("topicMember")
data class TopicMember(
        val uid: String,
        val handle: String,
        val imgUri: String
)

@JsonTypeName("topicMemberships")
data class TopicMemberships(
        val members: Set<TopicMember>
)