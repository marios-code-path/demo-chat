package com.demo.chat.deploy.app.memory

import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.User
import com.demo.chat.service.IndexService
import com.demo.chat.service.impl.memory.index.InMemoryIndex
import com.demo.chat.service.impl.memory.index.IndexEntryEncoder
import com.demo.chat.service.impl.memory.index.IndexSearchRequest
import com.demo.chat.service.impl.memory.index.StringToKeyEncoder
import org.springframework.context.annotation.Bean

open class InMemoryIndexConfiguration<T, E>(
        private val key: StringToKeyEncoder<T>,
        private val user: IndexEntryEncoder<User<T>>,
        private val message: IndexEntryEncoder<Message<T, E>>,
        private val topic: IndexEntryEncoder<MessageTopic<T>>) {
    @Bean
    open fun userIndex(): IndexService<T, User<T>, IndexSearchRequest> = InMemoryIndex(user, key)

    @Bean
    open fun messageIndex(): IndexService<T, Message<T, E>, IndexSearchRequest> = InMemoryIndex(message, key)

    @Bean
    open fun topicIndex():IndexService<T, MessageTopic<T>, IndexSearchRequest> = InMemoryIndex(topic, key)
}