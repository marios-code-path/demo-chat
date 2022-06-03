package com.demo.chat.config.index.memory

import com.demo.chat.config.IndexServiceBeans
import com.demo.chat.domain.*
import com.demo.chat.domain.AuthMetadata
import com.demo.chat.service.impl.lucene.index.IndexEntryEncoder
import com.demo.chat.service.impl.lucene.index.LuceneIndex
import com.demo.chat.service.impl.lucene.index.StringToKeyEncoder

open class LuceneIndexBeans<T, V, P: String>(
    private val key: StringToKeyEncoder<T>,
    private val user: IndexEntryEncoder<User<T>>,
    private val message: IndexEntryEncoder<Message<T, V>>,
    private val topic: IndexEntryEncoder<MessageTopic<T>>,
    private val membership: IndexEntryEncoder<TopicMembership<T>>,
    private val authMeta: IndexEntryEncoder<AuthMetadata<T, P>>
) : IndexServiceBeans<T, V, IndexSearchRequest, P> {
    override fun userIndex() = LuceneIndex(user, key) { t -> t.key }

    override fun messageIndex() = LuceneIndex(message, key) { t -> t.key }

    override fun topicIndex() = LuceneIndex(topic, key) { t -> t.key }

    override fun membershipIndex() = LuceneIndex(membership, key) { t -> Key.funKey(t.key) }

    override fun authMetaIndex() = LuceneIndex(authMeta, key) { t -> t.key }
}