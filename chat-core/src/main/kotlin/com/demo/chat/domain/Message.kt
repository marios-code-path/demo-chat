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
interface MessageKey<K, TK> : Key<K> {
    val dest: TK
    val timestamp: Instant
}

interface UserMessageKey<K, TK, U> : MessageKey<K, TK> {
    val userId: U

    companion object Factory {
        fun <K, TK, U> create(messageId: K, topic: TK, member: U): UserMessageKey<K, TK, U> = object : UserMessageKey<K, TK, U> {
            override val id: K
                get() = messageId
            override val dest: TK
                get() = topic
            override val userId: U
                get() = member
            override val timestamp: Instant
                get() = Instant.now()
        }

        fun <K, TK, U> create(messageKey: Key<K>, topic: TK, member: U): UserMessageKey<K, TK, U> = object : UserMessageKey<K, TK, U> {
            override val id: K
                get() = messageKey.id
            override val dest: TK
                get() = topic
            override val userId: U
                get() = member
            override val timestamp: Instant
                get() = Instant.now()
        }
    }
}


@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
interface Message<K, V> : KeyDataPair<K, V> {
    val visible: Boolean

    companion object Factory {
        fun <K, V> create(key: Key<K>, value: V, visible: Boolean): Message<K, V> = object : Message<K, V> {
            override val key: Key<K>
                get() = key
            override val data: V
                get() = value
            override val visible: Boolean
                get() = visible
        }
    }
}

// TODO :  Relax requirements (TextMessage to Message) on the Persistence layer so we can identify any Message Key and Payload.
@JsonTypeName("TextMessage")
interface TextMessage<K> : Message<K, String> {
    companion object Factory {
        fun <K, T, U> create(messageId: K, topic: T, member: U, stringOfData: String): TextMessage<K> = object : TextMessage<K> {
            override val key: Key<K>
                get() = UserMessageKey.create(messageId, topic, member)
            override val data: String
                get() = stringOfData
            override val visible: Boolean
                get() = true
        }

        fun <K> create(key: Key<K>, text: String, visible: Boolean): TextMessage<K> = object : TextMessage<K> {
            override val key: Key<K>
                get() = key
            override val data: String
                get() = text
            override val visible: Boolean
                get() = visible
        }
    }
}