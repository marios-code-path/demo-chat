package com.demo.chat

import java.util.*

data class UserRequest(val id: UUID)
data class UserCreateRequest(val name: String, val handle: String, val imgUri: String)