package com.demo.chat.domain

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import java.time.Instant

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
// user subscribes to 'james'
//    subscribe user="0000-000", id="1234-4567..."
//    user receives Stat object
//        contains [TOPIC_NAME, id] pairs
//        contains banner
//        etc
// user subscribes to MESSAGE topic
//    subscribe  user="0000-000" id="1234-4589..."
// user sends "TEXT" to MESSAGE
//    access denied, user not a member
// user joins channel
//    user sends "<my_user_id>" to MEMBERSHIP
// user sends "TEXT" to MESSAGE
//     user receives "TEXT" from MESSAGE [user_id = mario]
//     user receives "HELLO" from MESSAGE [user_id = james]
//     user receives "HOLA" from MESSAGE [user_id = lucas]
//     user receives "BROADCAST" from MESSAGE [user_id=james]
// user sends id to MEMBERSHIP
//    user sends message "<my_user_id>" to MEMBERSHIP
// user sends "TEXT" to MESSAGE
//    access denied, user not a member
@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
@JsonTypeName("MessageKey")
interface MessageKey<T> : Key<T> {
    val dest: T
    val timestamp: Instant

    companion object Factory {
        @JvmStatic
        fun <T> create(messageId: T, dest: T): MessageKey<T> = object : MessageKey<T> {
            override val id: T
                get() = messageId
            override val dest: T
                get() = dest
            override val timestamp: Instant
                get() = Instant.now()
        }
    }
}

interface UserMessageKey<T> : MessageKey<T> {
    val userId: T

    companion object Factory {
        fun <T> create(messageId: T, topic: T, member: T): UserMessageKey<T> = object : UserMessageKey<T> {
            override val id: T
                get() = messageId
            override val dest: T
                get() = topic
            override val userId: T
                get() = member
            override val timestamp: Instant
                get() = Instant.now()
        }

        fun <T> create(messageKey: Key<T>, topic: T, member: T): UserMessageKey<T> = object : UserMessageKey<T> {
            override val id: T
                get() = messageKey.id
            override val dest: T
                get() = topic
            override val userId: T
                get() = member
            override val timestamp: Instant
                get() = Instant.now()
        }
    }
}

@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
interface Message<T, E> : KeyDataPair<T, E> {
    val visible: Boolean

    companion object Factory {
        fun <T, E> create(key: Key<T>, value: E, visible: Boolean): Message<T, E> = object : Message<T, E> {
            override val key: Key<T>
                get() = key
            override val data: E
                get() = value
            override val visible: Boolean
                get() = visible
        }
    }
}

// TODO :  Relax requirements (TextMessage to Message) on the Persistence layer so we can identify any Message Key and Payload.
@JsonTypeName("TextMessage")
interface TextMessage<T> : Message<T, String> {
    override val key: UserMessageKey<T>

    companion object Factory {
        fun <T> create(messageId: T, topic: T, member: T, stringOfData: String): TextMessage<T> = object : TextMessage<T> {
            override val key: UserMessageKey<T>
                get() = UserMessageKey.create(messageId, topic, member)
            override val data: String
                get() = stringOfData
            override val visible: Boolean
                get() = true
        }

        fun <T> create(key: UserMessageKey<T>, text: String, visible: Boolean): TextMessage<T> = object : TextMessage<T> {
            override val key: UserMessageKey<T>
                get() = key
            override val data: String
                get() = text
            override val visible: Boolean
                get() = visible
        }
    }
}