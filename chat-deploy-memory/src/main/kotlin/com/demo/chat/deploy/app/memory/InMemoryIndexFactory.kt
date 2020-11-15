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

object UserIndexEntryEncoder : IndexEntryEncoder<User<UUID>> {
    override fun apply(t: User<UUID>) = listOf(
            Pair("key", t.key.id.toString()),
            Pair("handle", t.handle),
            Pair("name", t.name)
    )
}

object MessageIndexEntryEncoder : IndexEntryEncoder<Message<UUID, String>> {
    override fun apply(t: Message<UUID, String>) = listOf(
            Pair("key", t.key.id.toString()),
            Pair("text", t.data)
    )
}

object TopicIndexEntryEncoder : IndexEntryEncoder<MessageTopic<UUID>> {
    override fun apply(t: MessageTopic<UUID>)= listOf(
            Pair("key", t.key.id.toString()),
            Pair("name", t.data)
    )

}