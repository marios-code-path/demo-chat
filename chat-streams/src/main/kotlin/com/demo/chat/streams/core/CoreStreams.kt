package com.demo.chat.streams.core

import org.springframework.cloud.stream.annotation.Input
import org.springframework.cloud.stream.annotation.Output
import org.springframework.messaging.SubscribableChannel

interface CoreStreams {
    @Input(USER)
    fun userRequestSource(): SubscribableChannel

    @Output(USER)
    fun userReceive(): SubscribableChannel

    @Output(USEROUT)
    fun userOutputSink(): SubscribableChannel

    @Input(MESSAGE)
    fun messageSink(): SubscribableChannel

    @Output(MESSAGEOUT)
    fun messageOutSink(): SubscribableChannel

    @Input(TOPIC)
    fun topicSink(): SubscribableChannel

    @Output(TOPICOUT)
    fun topicOutputSink(): SubscribableChannel

    @Input(MEMBERSHIP)
    fun membershipSink(): SubscribableChannel

    @Output(MEMBERSHIPOUT)
    fun membershipOutSink(): SubscribableChannel

    companion object {
        const val USER: String = "USER"
        const val USEROUT: String = "USEROUT"
        const val MESSAGE: String = "MESSAGE"
        const val MESSAGEOUT: String = "MESSAGEOUT"
        const val TOPIC: String = "TOPIC"
        const val TOPICOUT: String = "TOPICOUT"
        const val MEMBERSHIP: String = "MEMBERSHIP"
        const val MEMBERSHIPOUT: String = "MEMBERSHIPOUT"
    }
}

data class UserCreateRequest(val name: String, val handle: String, val imgUri: String)

data class MessageTopicRequest(val name: String)

data class TopicMembershipRequest<T>(val uid: T, val roomId: T)

data class MessageSendRequest<T, V>(val msg: V, val from: T, val dest: T)

