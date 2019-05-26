package com.demo.chat.chatconsumers

import com.demo.chat.domain.User
import com.demo.chat.domain.UserKey
import java.time.Instant
import java.util.*

data class UserRequest(val userHandle: String)
data class UserCreateRequest(val name: String, val userHandle: String)
data class UserRequestId(val userId: UUID)

data class UserResponse(val user: ChatUser)

data class ChatUser(
        override val key: ChatUserKey,
        override val name: String,
        override val timestamp: Instant
) : User<UserKey>

data class ChatUserKey(
        override val userId: UUID,
        override val handle: String
) : UserKey