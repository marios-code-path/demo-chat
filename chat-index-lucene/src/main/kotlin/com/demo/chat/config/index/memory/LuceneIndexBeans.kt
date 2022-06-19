package com.demo.chat.config.index.memory

import com.demo.chat.config.IndexServiceBeans
import com.demo.chat.domain.*
import com.demo.chat.domain.lucene.IndexEntryEncoder
import com.demo.chat.service.MembershipIndexService
import com.demo.chat.service.MessageIndexService
import com.demo.chat.service.TopicIndexService
import com.demo.chat.service.UserIndexService
import org.springframework.context.annotation.Bean

open class LuceneIndexBeans<T>(
    private val typeUtil: TypeUtil<T>,
    private val user: IndexEntryEncoder<User<T>>,
    private val message: IndexEntryEncoder<Message<T, String>>,
    private val topic: IndexEntryEncoder<MessageTopic<T>>,
    private val membership: IndexEntryEncoder<TopicMembership<T>>
) : IndexServiceBeans<T, String, IndexSearchRequest> {
    private val stringToKey: (String) -> Key<T> = { str -> Key.funKey(typeUtil.fromString(str)) }

    @Bean
    override fun userIndex(): UserIndexService<T, IndexSearchRequest> =
        UserLuceneIndex(user, stringToKey) { t -> t.key }

    @Bean
    override fun messageIndex(): MessageIndexService<T, String, IndexSearchRequest> =
        MessageLuceneIndex(message, stringToKey) { t -> t.key }

    @Bean
    override fun topicIndex(): TopicIndexService<T, IndexSearchRequest> =
        TopicLuceneIndex(topic, stringToKey) { t -> t.key }

    @Bean
    override fun membershipIndex(): MembershipIndexService<T, IndexSearchRequest> =
        MembershipLuceneIndex(membership, stringToKey) { t -> Key.funKey(t.key) }
}