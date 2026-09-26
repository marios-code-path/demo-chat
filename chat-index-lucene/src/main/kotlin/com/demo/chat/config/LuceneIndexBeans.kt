package com.demo.chat.config

import com.demo.chat.domain.knownkey.RootKeys

import com.demo.chat.domain.knownkey.ChatDomain

import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.TypeUtil
import com.demo.chat.index.lucene.domain.IndexEntryEncoder
import com.demo.chat.index.lucene.impl.*
import com.demo.chat.service.core.*
import com.demo.chat.service.security.AuthMetaIndex
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(
    prefix = "app.service.core",
    name = ["index"],
    havingValue = "lucene",
    matchIfMissing = true
)
open class LuceneIndexBeans<T>(
    private val typeUtil: TypeUtil<T>,
    private val rootKeys: RootKeys<T>,
    private val keyValueFieldEntries: ObjectProvider<KeyValueIndexFieldsEntry>
) : IndexServiceBeans<T, String, IndexSearchRequest> {

    /**
     * The index stores the id text. A found key carries the root of the index
     * domain, read when the query runs. See `CHAT-avduuqwp`.
     */
    private fun keysIn(domain: ChatDomain): (String) -> Key<T> =
        { str -> Key.of(typeUtil.fromString(str), rootKeys.of(domain).id) }

    @Bean
    override fun userIndex(): UserIndexService<T, IndexSearchRequest> =
        UserLuceneIndex(IndexEntryEncoder.ofUser(), keysIn(ChatDomain.USER)) { t -> t.key }

    @Bean
    override fun messageIndex(): MessageIndexService<T, String, IndexSearchRequest> =
        MessageLuceneIndex(IndexEntryEncoder.ofMessage(), keysIn(ChatDomain.MESSAGE)) { t -> t.key }

    @Bean
    override fun topicIndex(): TopicIndexService<T, IndexSearchRequest> =
        TopicLuceneIndex(IndexEntryEncoder.ofTopic(), keysIn(ChatDomain.MESSAGE_TOPIC)) { t -> t.key }

    @Bean
    override fun membershipIndex(): MembershipIndexService<T, IndexSearchRequest> =
        MembershipLuceneIndex(IndexEntryEncoder.ofTopicMembership(), keysIn(ChatDomain.TOPIC_MEMBERSHIP)) { t ->
            Key.of(t.key, rootKeys.of(ChatDomain.TOPIC_MEMBERSHIP).id)
        }

    @Bean
    override fun authMetadataIndex(): AuthMetaIndex<T, IndexSearchRequest> =
        AuthMetaIndexLucene(typeUtil, rootKeys)

    @Bean
    override fun KVPairIndex(): KeyValueIndexService<T, IndexSearchRequest> =
        KeyValueLuceneIndex(
            typeUtil,
            rootKeys,
            IndexEntryEncoder.ofKeyValueFields(
                TypedKeyValueIndexFields(keyValueFieldEntries.orderedStream().toList())
            )
        )
}