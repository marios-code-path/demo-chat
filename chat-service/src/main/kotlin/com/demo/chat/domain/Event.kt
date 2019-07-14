package com.demo.chat.domain

import java.util.*

/* TODO raise EventKey as supertype to MessageKey */
interface EventKey {
    val id: UUID
    companion object Factory {
        fun create(id: UUID): EventKey = object : EventKey {
            override val id: UUID
                get() = id
        }
    }
}

interface Event<T> {
    val key: EventKey
    val data: T
    companion object Factory {
        fun <T>create(key: EventKey, data: T): Event<T> = object : Event<T> {
            override val key: EventKey
                get() = key
            override val data: T
                get() = data
        }
    }
}