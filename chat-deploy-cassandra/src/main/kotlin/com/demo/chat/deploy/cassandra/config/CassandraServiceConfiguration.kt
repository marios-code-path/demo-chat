package com.demo.chat.deploy.cassandra.config

import com.demo.chat.config.KeyServiceBeans
import com.demo.chat.controller.config.IndexControllersConfiguration
import com.demo.chat.controller.config.KeyControllersConfiguration
import com.demo.chat.controller.config.PersistenceControllersConfiguration
import com.demo.chat.deploy.cassandra.keygen.AtomicLongKeyGenerator
import com.demo.chat.deploy.cassandra.keygen.CassandraUUIDKeyGenerator
import com.demo.chat.domain.SnowflakeGenerator
import com.demo.chat.domain.TypeUtil
import com.demo.chat.repository.cassandra.*
import com.demo.chat.service.IKeyGenerator
import com.demo.chat.service.IKeyService
import com.demo.chat.service.persistence.KeyServiceCassandra
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import java.util.*
import kotlin.math.abs
import kotlin.random.Random

open class CassandraServiceConfiguration {
    @Configuration
    @ConditionalOnProperty(prefix = "app.service.core", name = ["index"])
    class CoreIndexBeans<T>(
        cassandra: ReactiveCassandraTemplate,
        userHandleRepo: ChatUserHandleRepository<T>,
        roomRepo: TopicRepository<T>,
        nameRepo: TopicByNameRepository<T>,
        byMemberRepo: TopicMembershipByMemberRepository<T>,
        byMemberOfRepo: TopicMembershipByMemberOfRepository<T>,
        byUserRepo: ChatMessageByUserRepository<T>,
        byTopicRepo: ChatMessageByTopicRepository<T>,
        principalRepo: AuthMetadataByPrincipalRepository<T>,
        targetRepo: AuthMetadataByTargetRepository<T>,
        typeUtil: TypeUtil<T>
    ) : CassandraIndexServices<T>(
        cassandra,
        userHandleRepo,
        roomRepo,
        nameRepo,
        byMemberRepo,
        byMemberOfRepo,
        byUserRepo,
        byTopicRepo,
        principalRepo,
        targetRepo,
        typeUtil
    ) {
        @Configuration
        class IndexControllers : IndexControllersConfiguration()
    }

    @Configuration
    @ConditionalOnProperty(prefix = "app.service.core", name = ["persistence"])
    class CorePersistenceBeans<T>(
        keyService: IKeyService<T>,
        userRepo: ChatUserRepository<T>,
        topicRepo: TopicRepository<T>,
        messageRepo: ChatMessageRepository<T>,
        membershipRepo: TopicMembershipRepository<T>,
        authmetaRepo: AuthMetadataRepository<T>
    ) : CassandraPersistenceServices<T>(keyService, userRepo, topicRepo, messageRepo, membershipRepo, authmetaRepo) {
        @Configuration
        class PersistenceControllers : PersistenceControllersConfiguration()
    }

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

        @Configuration
        class KeyControllers : KeyControllersConfiguration()
    }

}