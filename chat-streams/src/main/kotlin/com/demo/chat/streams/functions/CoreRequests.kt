package com.demo.chat.streams.functions

data class UserCreateRequest(val name: String, val handle: String, val imgUri: String)

data class MessageTopicRequest(val name: String)

interface MembershipRequest<T> {
    val principal: T
    val destination: T
}

data class TopicMembershipRequest<T>(override val principal: T, override val destination: T) : MembershipRequest<T>

data class MessageSendRequest<T, V>(val msg: V, val from: T, val dest: T)

