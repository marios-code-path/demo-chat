package com.demo.chat.deploy.app.memory

import com.demo.chat.deploy.config.core.IndexServiceConfiguration
import com.demo.chat.domain.*
import com.demo.chat.service.impl.lucene.index.LuceneIndex
import com.demo.chat.service.impl.lucene.index.IndexEntryEncoder
import com.demo.chat.service.impl.lucene.index.StringToKeyEncoder

open class InMemoryIndexConfiguration<T, V>(
        private val key: StringToKeyEncoder<T>,
        private val user: IndexEntryEncoder<User<T>>,
        private val message: IndexEntryEncoder<Message<T, V>>,
        private val topic: IndexEntryEncoder<MessageTopic<T>>,
        private val membership: IndexEntryEncoder<TopicMembership<T>>,
) : IndexServiceConfiguration<T, V, IndexSearchRequest> {
    override fun userIndex() = LuceneIndex(user, key) { t -> t.key }

    override fun messageIndex() = LuceneIndex(message, key) { t -> t.key }

    override fun topicIndex() = LuceneIndex(topic, key) { t -> t.key }

    override fun membershipIndex() = LuceneIndex(membership, key) { t -> Key.funKey(t.key) }
}