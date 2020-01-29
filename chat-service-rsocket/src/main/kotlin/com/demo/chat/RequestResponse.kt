package com.demo.chat

import java.util.*

data class UserRequestId(val userId: UUID)
data class UserCreateRequest(val name: String, val userHandle: String, val imgUri: String)