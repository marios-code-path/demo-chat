package com.demo.chat.domain

import java.time.Instant
import java.util.*

interface UserKey {
    val userId: UUID
    val handle: String
}

interface User<T : UserKey> {
    val key: T
    val name: String
    val timestamp: Instant
}