package com.demo.chat.domain

import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.time.Instant
import java.util.*

@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
interface User<out K> {
    val key: K
    val name: String
    val timestamp: Instant
}

interface UserKey {
    val userId: UUID
    val handle: String
}

