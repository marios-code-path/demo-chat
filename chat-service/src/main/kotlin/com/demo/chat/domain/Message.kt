package com.demo.chat.domain

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import java.time.Instant
import java.util.*

/* TODO: visible flag needs to be in Message not Key */
/* TODO: create Factory with visible flag too */
@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
@JsonSubTypes(JsonSubTypes.Type(TextMessage::class),
        JsonSubTypes.Type(InfoAlert::class))
interface Message<out K, out V> {
    val key: K
    val value: V
    val visible: Boolean

    companion object Factory {
        fun <K, V> create(key: K, value: V, visible: Boolean): Message<K, V> = object : Message<K, V> {
            override val key: K
                get() = key
            override val value: V
                get() = value
            override val visible: Boolean
                get() = visible
        }
    }
}

interface MessageKey : EventKey {
    val timestamp: Instant
}

interface TopicMessageKey : MessageKey {
    override val id: UUID
    val topicId: UUID
    override val timestamp: Instant
}

interface TextMessageKey : TopicMessageKey {
    val userId: UUID

    companion object Factory {
        fun create(messageId: UUID, topic: UUID, member: UUID): TextMessageKey = object : TextMessageKey {
            override val id: UUID
                get() = messageId
            override val topicId: UUID
                get() = topic
            override val userId: UUID
                get() = member
            override val timestamp: Instant
                get() = Instant.now()
        }

        fun create(key: EventKey, topic: UUID, member: UUID): TextMessageKey = object : TextMessageKey {
            override val id: UUID
                get() = key.id
            override val topicId: UUID
                get() = topic
            override val userId: UUID
                get() = member
            override val timestamp: Instant
                get() = Instant.now()
        }
    }

}

interface AlertMessageKey : TopicMessageKey {
    //  private val ttl: Int
    companion object Factory {
        fun create(messageId: UUID, topic: UUID): AlertMessageKey = object : AlertMessageKey {
            override val id: UUID
                get() = messageId
            override val topicId: UUID
                get() = topic
            override val timestamp: Instant
                get() = Instant.now()
        }
    }
}

///@JsonDeserialize(using = TextMessageDeserializer::class)
@JsonTypeName("ChatMessage")
interface TextMessage : Message<TextMessageKey, String> {
    companion object Factory {
        fun create(messageId: UUID, topic: UUID, member: UUID, stringOfData: String): TextMessage = object : TextMessage {
            override val key: TextMessageKey
                get() = TextMessageKey.create(messageId, topic, member)
            override val value: String
                get() = stringOfData
            override val visible: Boolean
                get() = true
        }

        fun create(key: TextMessageKey, text: String, visible: Boolean): TextMessage = object : TextMessage {
            override val key: TextMessageKey
                get() = key
            override val value: String
                get() = text
            override val visible: Boolean
                get() = visible

        }
    }
}

@JsonTypeName("InfoAlert")
interface InfoAlert : Message<AlertMessageKey, RoomMetaData> {
    companion object Factory {
        fun create(messageId: UUID, topic: UUID, meta: RoomMetaData): InfoAlert = object : InfoAlert {
            override val key: AlertMessageKey
                get() = AlertMessageKey.create(messageId, topic)
            override val value: RoomMetaData
                get() = meta
            override val visible: Boolean
                get() = true
        }

        fun create(key: AlertMessageKey, meta: RoomMetaData, visible: Boolean): InfoAlert = object : InfoAlert {
            override val key: AlertMessageKey
                get() = key
            override val value: RoomMetaData
                get() = meta
            override val visible: Boolean
                get() = visible
        }
    }
}

@JsonTypeName("LeaveAlert")
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

        fun create(key: AlertMessageKey, member: UUID, visible: Boolean): LeaveAlert = object : LeaveAlert {
            override val key: AlertMessageKey
                get() = key
            override val value: UUID
                get() = member
            override val visible: Boolean
                get() = visible
        }
    }
}

@JsonTypeName("JoinAlert")
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

        fun create(key: AlertMessageKey, member: UUID, visible: Boolean): JoinAlert = object : JoinAlert {
            override val key: AlertMessageKey
                get() = key
            override val value: UUID
                get() = member
            override val visible: Boolean
                get() = visible
        }
    }
}

@JsonTypeName("PauseAlert")
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

        fun create(key: AlertMessageKey, member: UUID, visible: Boolean): PauseAlert = object : PauseAlert {
            override val key: AlertMessageKey
                get() = key
            override val value: UUID
                get() = member
            override val visible: Boolean
                get() = visible
        }
    }
}