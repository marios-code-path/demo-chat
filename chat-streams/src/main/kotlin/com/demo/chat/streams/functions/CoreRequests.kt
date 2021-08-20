package com.demo.chat.streams.functions

data class UserCreateRequest(val name: String, val handle: String, val imgUri: String)

data class MessageTopicRequest(val name: String)

data class TopicMembershipRequest<T>(val pid: T, val destId: T)

data class MessageSendRequest<T, V>(val msg: V, val from: T, val dest: T)

