package com.demo.chat.deploy.app.memory

import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.User
import com.demo.chat.service.impl.memory.index.InMemoryIndex
import com.demo.chat.service.impl.memory.index.IndexEntryEncoder
import com.demo.chat.service.impl.memory.index.StringToKeyEncoder
import org.springframework.context.annotation.Bean
import java.util.*

open class InMemoryIndexFactory<T, E, Q>(
        private val key: StringToKeyEncoder<T>,
        private val user: IndexEntryEncoder<User<T>>,
        private val message: IndexEntryEncoder<Message<T, E>>,
        private val topic: IndexEntryEncoder<MessageTopic<T>>) {
    @Bean
    open fun userIndex() = InMemoryIndex(user, key)

    @Bean
    open fun messageIndex() = InMemoryIndex(message, key)

    @Bean
    open fun topicIndex() = InMemoryIndex(topic, key)
}