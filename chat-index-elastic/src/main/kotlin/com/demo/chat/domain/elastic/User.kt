package com.demo.chat.domain.elastic

import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.PersistenceConstructor
import org.springframework.data.elasticsearch.annotations.Document
import org.springframework.data.elasticsearch.annotations.Field
import java.time.Instant

@Document(indexName = "user", type = "user")
data class ChatUser<T>(
        override val key: ChatUserKey<T>,
        @Field
        override val name: String,
        @Field
        override val handle: String,
        @Field
        override val imageUri: String,
        @Field
        override val timestamp: Instant,
        @Id
        val id: String = key.id.toString()
) : User<T>

data class ChatUserKey<T>(
        override val id: T,
        override val empty:Boolean = false
) : Key<T>