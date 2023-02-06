package com.demo.chat.persistence.cassandra.config

import com.demo.chat.config.KeyServiceBeans
import com.demo.chat.domain.SnowflakeGenerator
import com.demo.chat.persistence.cassandra.domain.keygen.CassandraUUIDKeyGenerator
import com.demo.chat.persistence.cassandra.impl.KeyServiceCassandra
import com.demo.chat.persistence.cassandra.repository.*
import com.demo.chat.service.core.IKeyGenerator
import com.demo.chat.service.core.IKeyService
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import java.util.*

@Configuration
class PersistenceConfiguration {

    @Configuration
    @ConditionalOnProperty(prefix = "app.service.core", name = ["persistence"])
    class CorePersistenceBeans<T>(
        keyService: IKeyService<T>,
        userRepo: ChatUserRepository<T>,
        topicRepo: TopicRepository<T>,
        messageRepo: ChatMessageRepository<T>,
        membershipRepo: TopicMembershipRepository<T>,
        authmetaRepo: AuthMetadataRepository<T>
    ) : CassandraPersistenceServices<T>(keyService, userRepo, topicRepo, messageRepo, membershipRepo, authmetaRepo)

    // enforce number on nodeid
    @Value("\${app.nodeid:0}")
    lateinit var nodeId: String

    @Bean
    @ConditionalOnProperty("app.service.core.key", havingValue = "long")
    open fun longKeyGen(): IKeyGenerator<Long> = when (nodeId) {
        null, "0", "" -> SnowflakeGenerator()
        else -> SnowflakeGenerator(nodeId.toInt())
    }

    @Bean
    @ConditionalOnProperty("app.service.core.key", havingValue = "uuid")
    open fun uuidKeyGen(): IKeyGenerator<UUID> = CassandraUUIDKeyGenerator()

    @Configuration
    @ConditionalOnProperty(prefix = "app.service.core", name = ["key"])
    class CoreKeyBeans<T>(
        val reactiveTemplate: ReactiveCassandraTemplate,
        val keyGenerator: IKeyGenerator<T>
    ) : KeyServiceBeans<T> {
        @Bean
        override fun keyService(): IKeyService<T> = KeyServiceCassandra(reactiveTemplate, keyGenerator)
    }

}