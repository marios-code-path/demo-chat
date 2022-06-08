package com.demo.chat.deploy.app.memory

import com.demo.chat.config.index.memory.LuceneIndexBeans
import com.demo.chat.config.memory.InMemoryPersistenceBeans
import com.demo.chat.config.memory.LongKeyServiceBeans
import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.User
import com.demo.chat.domain.lucene.IndexEntryEncoder
import com.demo.chat.service.IKeyService
import com.demo.chat.service.IndexService
import com.demo.chat.service.PersistenceStore
import com.demo.chat.service.TopicPubSubService
import com.demo.chat.service.impl.memory.messaging.MemoryTopicPubSubService
import com.demo.chat.service.impl.memory.persistence.UserPersistenceInMemory
import com.demo.chat.service.security.AuthMetaIndexLucene
import com.demo.chat.service.security.AuthMetaPersistenceInMemory
import com.demo.chat.service.security.SecretsStore
import com.demo.chat.service.security.SecretsStoreInMemory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

open class MemoryResourceConfiguration {

    @Configuration
    class KeyServiceBeans : LongKeyServiceBeans()

    @Bean
    open fun authMetaPersistence(keySvc: IKeyService<Long>):
            PersistenceStore<Long, AuthMetadata<Long>> =
        AuthMetaPersistenceInMemory(keySvc) { t -> t.key }

    @Bean
    open fun authMetaIndex(): IndexService<Long, AuthMetadata<Long>, IndexSearchRequest> =
        AuthMetaIndexLucene(TypeUtil.LongUtil)

    @Bean
    open fun passwordStoreInMemory(): SecretsStore<Long> = SecretsStoreInMemory()

    @Bean
    @ConditionalOnProperty(prefix = "app.service.core", name = ["pubsub"])
    open fun memoryPubSub(): TopicPubSubService<Long, String> = MemoryTopicPubSubService()

    @Configuration
    class PersistenceBeans(keyFactory: KeyServiceBeans) :
        InMemoryPersistenceBeans<Long, String>(keyFactory.keyService())

    @Configuration
    class IndexBeans : LuceneIndexBeans<Long, String>(
        TypeUtil.LongUtil,
        IndexEntryEncoder.ofUser(),
        IndexEntryEncoder.ofMessage(),
        IndexEntryEncoder.ofTopic(),
        IndexEntryEncoder.ofTopicMembership()
    )
}