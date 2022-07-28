package com.demo.chat.deploy.memory.config

import com.demo.chat.config.KeyServiceBeans
import com.demo.chat.config.index.lucene.LuceneIndexBeans
import com.demo.chat.config.memory.InMemoryPersistenceBeans
import com.demo.chat.config.memory.LongKeyServiceBeans
import com.demo.chat.domain.SnowflakeGenerator
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.UUIDUtil
import com.demo.chat.domain.lucene.IndexEntryEncoder
import com.demo.chat.service.IKeyGenerator
import com.demo.chat.service.IKeyService
import com.demo.chat.service.TopicPubSubService
import com.demo.chat.service.impl.memory.messaging.MemoryTopicPubSubService
import com.demo.chat.service.impl.memory.persistence.KeyServiceInMemory
import com.demo.chat.service.security.SecretsStoreInMemory
import com.demo.chat.service.security.UserCredentialSecretsStore
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.*

open class MemoryResourceConfiguration {
    @Bean
    @ConditionalOnProperty("app.service.core.key", havingValue = "uuid")
    fun uuidTypeUtil(): TypeUtil<UUID> = UUIDUtil()

    @Bean
    @ConditionalOnProperty("app.service.core.key", havingValue = "long")
    fun longTypeUtil(): TypeUtil<Long> = TypeUtil.LongUtil

    @ConditionalOnProperty("app.service.core.key", havingValue = "long")
    @Configuration
    class LongMemoryKeyServiceBeans : LongKeyServiceBeans()

    @ConditionalOnProperty("app.service.core.key", havingValue = "uuid")
    @Configuration
    class UUIDMemoryKeyServiceBeans : KeyServiceBeans<UUID> {
        val idGenerator: IKeyGenerator<Long> = SnowflakeGenerator()

        override fun keyService(): IKeyService<UUID> =
            KeyServiceInMemory { UUID.nameUUIDFromBytes(idGenerator.nextKey().toString().encodeToByteArray()) }
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.service.core", name = ["secrets"])
    open fun <T>passwordStoreInMemory(typeUtil: TypeUtil<T>): UserCredentialSecretsStore<T> = SecretsStoreInMemory()

    @Bean
    @ConditionalOnProperty(prefix = "app.service.core", name = ["pubsub"])
    open fun <T> memoryPubSub(typeUtil: TypeUtil<T>): TopicPubSubService<T, String> = MemoryTopicPubSubService()

    @Configuration
    @ConditionalOnProperty(prefix = "app.service.core", name = ["persistence"])
    class PersistenceBeans<T>(keyFactory: KeyServiceBeans<T>) :
        InMemoryPersistenceBeans<T, String>(keyFactory.keyService())

    @Configuration
    @ConditionalOnProperty(prefix = "app.service.core", name = ["index"])
    class IndexBeans<T>(typeUtil: TypeUtil<T>) : LuceneIndexBeans<T>(
        typeUtil,
        IndexEntryEncoder.ofUser(),
        IndexEntryEncoder.ofMessage(),
        IndexEntryEncoder.ofTopic(),
        IndexEntryEncoder.ofTopicMembership()
    )
}