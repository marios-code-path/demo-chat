package com.demo.chat.config

import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.TypeUtil
import com.demo.chat.index.lucene.domain.IndexEntryEncoder
import com.demo.chat.index.lucene.impl.*
import com.demo.chat.service.core.*
import com.demo.chat.service.security.AuthMetaIndex
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.ConversionService

@Configuration
@ConditionalOnProperty(prefix = "app.service.core", name = ["index"])
open class LuceneIndexBeans<T>(
    private val typeUtil: TypeUtil<T>,
    private val cs: ConversionService
) : IndexServiceBeans<T, String, IndexSearchRequest> {

    private val stringToKey: (String) -> Key<T> = { str -> Key.funKey(typeUtil.fromString(str)) }

    @Bean
    override fun userIndex(): UserIndexService<T, IndexSearchRequest> =
        UserLuceneIndex(IndexEntryEncoder.ofUser(), stringToKey) { t -> t.key }

    @Bean
    override fun messageIndex(): MessageIndexService<T, String, IndexSearchRequest> =
        MessageLuceneIndex(IndexEntryEncoder.ofMessage(), stringToKey) { t -> t.key }

    @Bean
    override fun topicIndex(): TopicIndexService<T, IndexSearchRequest> =
        TopicLuceneIndex(IndexEntryEncoder.ofTopic(), stringToKey) { t -> t.key }

    @Bean
    override fun membershipIndex(): MembershipIndexService<T, IndexSearchRequest> =
        MembershipLuceneIndex(IndexEntryEncoder.ofTopicMembership(), stringToKey) { t -> Key.funKey(t.key) }

    @Bean
    override fun authMetadataIndex(): AuthMetaIndex<T, IndexSearchRequest> =
        AuthMetaIndexLucene(typeUtil)

    @Bean
    override fun KVPairIndex(): KeyValueIndexService<T, IndexSearchRequest> =
        KeyValueLuceneIndex(typeUtil, IndexEntryEncoder.ofConversionService(conversionService = cs) )
}