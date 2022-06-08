package com.demo.chat.config.index.memory

import com.demo.chat.config.IndexServiceBeans
import com.demo.chat.domain.*
import com.demo.chat.domain.lucene.IndexEntryEncoder
import com.demo.chat.service.IndexService
import com.demo.chat.service.impl.lucene.index.LuceneIndex
import org.springframework.context.annotation.Bean

open class LuceneIndexBeans<T, V>(
    private val typeUtil: TypeUtil<T>,
    private val user: IndexEntryEncoder<User<T>>,
    private val message: IndexEntryEncoder<Message<T, V>>,
    private val topic: IndexEntryEncoder<MessageTopic<T>>,
    private val membership: IndexEntryEncoder<TopicMembership<T>>
) : IndexServiceBeans<T, V, IndexSearchRequest> {
    private val stringToKey: (String) -> Key<T> = { str -> Key.funKey(typeUtil.fromString(str)) }

    @Bean
    override fun userIndex(): IndexService<T, User<T>, IndexSearchRequest> =
        LuceneIndex(user, stringToKey) { t -> t.key }

    @Bean
    override fun messageIndex(): IndexService<T, Message<T, V>, IndexSearchRequest> =
        LuceneIndex(message, stringToKey) { t -> t.key }

    @Bean
    override fun topicIndex(): IndexService<T, MessageTopic<T>, IndexSearchRequest> =
        LuceneIndex(topic, stringToKey) { t -> t.key }

    @Bean
    override fun membershipIndex(): IndexService<T, TopicMembership<T>, IndexSearchRequest> =
        LuceneIndex(membership, stringToKey) { t -> Key.funKey(t.key) }
}