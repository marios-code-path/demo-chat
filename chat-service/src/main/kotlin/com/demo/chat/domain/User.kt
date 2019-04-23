package com.demo.chat.domain

import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.time.Instant
import java.util.*

@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
interface User<out T> {
    val key: T
    val name: String
    val timestamp: Instant
}

interface UserKey {
    val userId: UUID
    val handle: String
}