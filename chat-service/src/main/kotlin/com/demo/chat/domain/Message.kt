package com.demo.chat.domain

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import java.time.Instant
import java.util.*

// Topics must have shapes: composed or non-composed [default shape]
// thus there should be 1 composition - binary expressed as a boolean map of all messages
// another composition is ability acknowledgment replies as the ID of the channel being communicated to.

// openTopic("membership").id("james").sendMessage(me.id)
// security -> denied "cannot send messages to non-membership topics"
// openTopic("messages").id("james").sendMessage("Hello")
// security -> can send messages on this topic
// openTopic("join_leave_activity").id("my_user_text_message_topic").sendMessage(me.id)
//
// topics use case  :
// chat texting = join/leave, message, stat[with alerts], broadcast
// security policy :
//
// Principle is allowed to join/leave a collection of known topics [called a room]:
// Leaving own topic causes a complete refresh, as all members are unsubscribed from topic.
//
// Principle is able to send messages to any topic it has permission to.
// Message composition is per-topic basis, or may vary message to message if allowed
// Security permission for message composition is given by topic [ max_message_size, encoding]
//
// Principle may enter Key/Value data into a unique stats object.
// This might include banners, interactivity model configuration, message pointers [last seen]
// The stat channel is the first channel you should subscribe to in order to know which other channels
// to join.
// //
// Principle may alert room.
// This causes a message to broadcast across all known topics of a room.
// write capability to this topic is default to Owner of room Principle is not Owner.
// (Principle may grant other users broadcast access)
//
// Principle subscribes and receives messages on a topic.
// messages are encoded and sent by other members.
// message size is dictated by policy of topic [ max_message_size ]
//
// order of events : init channel = stat for 'chat'.
// contains key/value pairs for all open rooms (their respective stat channels)
// Map looks like: {
//    mario: '0000-000'
//    james: '1234-4567'
//    lucas: '5678-4567'
//    bogus: '6789-5678'
// }
// in order for user subscribes to stat for user 'james'
//    user accesses Users.getby("handle=james"), or uses Map given from 'chat' stat
//        user receives User[id=1234-4567..., name, handle, uri...]
// user subscribes to id="1234-4567..."
//    user receives Map object
//        contains [topic:id] pairs
//        contains banner text
//        etc
// user subscribes to message topic
//    user subscribes to id="1234-4589..."
// user sends "TEXT" to message
//    access denied, user not a member
// user joins channel
//    user sends message "<my_user_id>" to join
// user sends "TEXT" to message
//     user receives "TEXT" from message [user_id = mario]
//     user receives "HELLO" from message [user_id = james]
//     user receives "HOLA" from message [user_id = lucas]
//     user receives "BROADCAST" from message [user_id=james]
// user sends id to join
//    user sends message "<my_user_id>" to join

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
    val topicId: UUID
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

@Deprecated("Alert stereotype no longer required")
@JsonTypeName("InfoAlert")
interface InfoAlert : Message<AlertMessageKey, TopicMetaData> {
    companion object Factory {
        fun create(messageId: UUID, topic: UUID, meta: TopicMetaData): InfoAlert = object : InfoAlert {
            override val key: AlertMessageKey
                get() = AlertMessageKey.create(messageId, topic)
            override val value: TopicMetaData
                get() = meta
            override val visible: Boolean
                get() = true
        }

        fun create(key: AlertMessageKey, meta: TopicMetaData, visible: Boolean): InfoAlert = object : InfoAlert {
            override val key: AlertMessageKey
                get() = key
            override val value: TopicMetaData
                get() = meta
            override val visible: Boolean
                get() = visible
        }
    }
}

@Deprecated("Alert stereotype no longer required")
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

@Deprecated("Alert stereotype no longer required")
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

@Deprecated("Alert stereotype no longer required")
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