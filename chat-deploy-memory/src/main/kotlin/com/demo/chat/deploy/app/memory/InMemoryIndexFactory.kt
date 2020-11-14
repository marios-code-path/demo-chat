package com.demo.chat.deploy.app.memory

import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.User
import com.demo.chat.service.IndexService
import com.demo.chat.service.impl.memory.index.InMemoryIndex
import com.demo.chat.service.impl.memory.index.IndexerFn
import org.springframework.context.annotation.Bean
import java.util.*

open class InMemoryIndexFactory<T, E, Q>(
        private val user: IndexerFn<User<T>>,
        private val message: IndexerFn<Message<T, E>>,
        private val topic: IndexerFn<MessageTopic<T>>) {
    @Bean
    open fun userIndex(): IndexService<T, User<T>, Q> = InMemoryIndex(user)

    @Bean
    open fun messageIndex(): IndexService<T, Message<T, E>, Q> = InMemoryIndex(message)

    @Bean
    open fun topicIndex(): IndexService<T, MessageTopic<T>, Q> = InMemoryIndex(topic)
}

object UserIndexerFn : IndexerFn<User<UUID>> {
    override fun apply(t: User<UUID>) = listOf(
            Pair("key", t.key.id.toString()),
            Pair("handle", t.handle),
            Pair("name", t.name)
    )
}

object MessageIndexerFn : IndexerFn<Message<UUID, String>> {
    override fun apply(t: Message<UUID, String>) = listOf(
            Pair("key", t.key.id.toString()),
            Pair("text", t.data)
    )
}

object TopicIndexerFn : IndexerFn<MessageTopic<UUID>> {
    override fun apply(t: MessageTopic<UUID>)= listOf(
            Pair("key", t.key.id.toString()),
            Pair("name", t.data)
    )

}