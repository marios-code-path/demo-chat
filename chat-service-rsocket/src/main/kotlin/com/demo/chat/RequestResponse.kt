package com.demo.chat

import com.demo.chat.domain.User
import com.demo.chat.domain.UserKey

data class UserRequest(val userHandle: String)
data class UserCreateRequest(val name: String, val userHandle: String)

data class UserResponse(val user: User<UserKey>)
