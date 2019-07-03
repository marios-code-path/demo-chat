package com.demo.chat.domain

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.time.Instant
import java.util.*

@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
@JsonSubTypes(JsonSubTypes.Type(TextMessage::class),
        JsonSubTypes.Type(InfoAlert::class))
interface Message<out K, out V> {
    val key: K
    val value: V
    val visible: Boolean
}

interface MessageKey {
    val msgId: UUID
    val timestamp: Instant
}

interface TopicMessageKey : MessageKey {
    override val msgId: UUID
    val topicId: UUID
    override val timestamp: Instant
}

interface TextMessageKey : TopicMessageKey {
    val userId: UUID
    companion object Factory {
        fun create(messageId: UUID, topic: UUID, member: UUID): TextMessageKey = object : TextMessageKey {
            override val msgId: UUID
                get() = messageId
            override val topicId: UUID
                get() = topic
            override val timestamp: Instant
                get() = Instant.now()
            override val userId: UUID
                get() = member
        }
    }
}

interface AlertMessageKey : TopicMessageKey {
    //  private val ttl: Int
    companion object Factory {
        fun create(messageId: UUID, topic: UUID): AlertMessageKey = object : AlertMessageKey {
            override val msgId: UUID
                get() = messageId
            override val topicId: UUID
                get() = topic
            override val timestamp: Instant
                get() = Instant.now()
        }
    }
}

interface TextMessage : Message<TextMessageKey, String> {
    companion object Factory {
        fun create(messageId: UUID, topic: UUID, member: UUID, stringOfData: String): TextMessage = object : TextMessage {
            override val key: TextMessageKey
                get() = TextMessageKey.create(messageId, topic, member)
            override val value: String
                get() = stringOfData
            override val visible: Boolean
                get() = false
        }
    }
}

interface InfoAlert : Message<AlertMessageKey, RoomMetaData> {
    companion object Factory {
        fun create(messageId: UUID, topic: UUID, meta: RoomMetaData): InfoAlert = object : InfoAlert {
            override val key: AlertMessageKey
                get() = AlertMessageKey.create(messageId, topic)
            override val value: RoomMetaData
                get() = meta
            override val visible: Boolean
                get() = false
        }
    }
}

interface LeaveAlert : Message<AlertMessageKey, UUID> {
    companion object Factory {
        fun create(messageId: UUID, topic: UUID, member: UUID): LeaveAlert = object : LeaveAlert {
            override val key: AlertMessageKey
                get() = AlertMessageKey.create(messageId, topic)
            override val value: UUID
                get() = member
            override val visible: Boolean
                get() = false
        }
    }
}

interface JoinAlert : Message<AlertMessageKey, UUID> {
    companion object Factory {
        fun create(messageId: UUID, topic: UUID, member: UUID): JoinAlert = object : JoinAlert {
            override val key: AlertMessageKey
                get() = AlertMessageKey.create(messageId, topic)
            override val value: UUID
                get() = member
            override val visible: Boolean
                get() = false
        }
    }
}

interface PauseAlert : Message<AlertMessageKey, UUID> {
    companion object Factory {
        fun create(messageId: UUID, topic: UUID, member: UUID): PauseAlert = object : PauseAlert {
            override val key: AlertMessageKey
                get() = AlertMessageKey.create(messageId, topic)
            override val value: UUID
                get() = member
            override val visible: Boolean
                get() = false
        }
    }
}


