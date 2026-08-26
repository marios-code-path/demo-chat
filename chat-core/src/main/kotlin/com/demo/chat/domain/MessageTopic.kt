package com.demo.chat.domain

import com.fasterxml.jackson.annotation.JsonTypeName
import java.util.*

interface MessageTopic<T> : KeyValuePair<T, String> {
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