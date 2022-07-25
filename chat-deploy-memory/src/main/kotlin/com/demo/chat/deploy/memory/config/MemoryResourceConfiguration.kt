package com.demo.chat.deploy.memory.config

import com.demo.chat.config.index.lucene.LuceneIndexBeans
import com.demo.chat.config.memory.InMemoryPersistenceBeans
import com.demo.chat.config.memory.LongKeyServiceBeans
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.lucene.IndexEntryEncoder
import com.demo.chat.service.TopicPubSubService
import com.demo.chat.service.impl.memory.messaging.MemoryTopicPubSubService
import com.demo.chat.service.security.SecretsStoreInMemory
import com.demo.chat.service.security.UserCredentialSecretsStore
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

open class MemoryResourceConfiguration {

    @Configuration
    class KeyServiceBeans : LongKeyServiceBeans()

    @Bean
    open fun passwordStoreInMemory(): UserCredentialSecretsStore<Long> = SecretsStoreInMemory()

    @Bean
    @ConditionalOnProperty(prefix = "app.service.core", name = ["pubsub"])
    open fun memoryPubSub(): TopicPubSubService<Long, String> = MemoryTopicPubSubService()

    @Configuration
    class PersistenceBeans(keyFactory: KeyServiceBeans) :
        InMemoryPersistenceBeans<Long, String>(keyFactory.keyService())

    @Configuration
    class IndexBeans : LuceneIndexBeans<Long>(
        TypeUtil.LongUtil,
        IndexEntryEncoder.ofUser(),
        IndexEntryEncoder.ofMessage(),
        IndexEntryEncoder.ofTopic(),
        IndexEntryEncoder.ofTopicMembership()
    )
}