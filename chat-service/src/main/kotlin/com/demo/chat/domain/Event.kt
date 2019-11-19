package com.demo.chat.domain

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import java.util.*

@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
@JsonTypeName("EventKey")
interface EventKey {
    val id: UUID
    companion object Factory {
        @JvmStatic
        fun create(id: UUID): EventKey = object : EventKey {
            override val id: UUID
                get() = id
        }
    }
}

@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
@JsonTypeName("Event")
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