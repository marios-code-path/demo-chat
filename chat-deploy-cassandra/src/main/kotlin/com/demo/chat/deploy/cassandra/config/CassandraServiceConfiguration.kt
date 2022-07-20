package com.demo.chat.deploy.cassandra.config

import com.demo.chat.config.KeyServiceBeans
import com.demo.chat.controller.config.IndexControllersConfiguration
import com.demo.chat.controller.config.KeyControllersConfiguration
import com.demo.chat.controller.config.PersistenceControllersConfiguration
import com.demo.chat.deploy.cassandra.keygen.AtomicLongKeyGenerator
import com.demo.chat.deploy.cassandra.keygen.CassandraUUIDKeyGenerator
import com.demo.chat.domain.TypeUtil
import com.demo.chat.repository.cassandra.*
import com.demo.chat.service.IKeyGenerator
import com.demo.chat.service.IKeyService
import com.demo.chat.service.persistence.KeyServiceCassandra
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
    class CoreIndexBeans(
        cassandra: ReactiveCassandraTemplate,
        userHandleRepo: ChatUserHandleRepository<UUID>,
        roomRepo: TopicRepository<UUID>,
        nameRepo: TopicByNameRepository<UUID>,
        byMemberRepo: TopicMembershipByMemberRepository<UUID>,
        byMemberOfRepo: TopicMembershipByMemberOfRepository<UUID>,
        byUserRepo: ChatMessageByUserRepository<UUID>,
        byTopicRepo: ChatMessageByTopicRepository<UUID>,
        principalRepo: AuthMetadataByPrincipalRepository<UUID>,
        targetRepo: AuthMetadataByTargetRepository<UUID>,
        typeUtil: TypeUtil<UUID>
    ) : CassandraIndexServices<UUID>(
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
        typeUtil::fromString,
        typeUtil
    ) {
        @Configuration
        class IndexControllers : IndexControllersConfiguration()
    }

    @Configuration
    @ConditionalOnProperty(prefix = "app.service.core", name = ["persistence"])
    class CorePersistenceBeans(
        keyService: IKeyService<UUID>,
        userRepo: ChatUserRepository<UUID>,
        topicRepo: TopicRepository<UUID>,
        messageRepo: ChatMessageRepository<UUID>,
        membershipRepo: TopicMembershipRepository<UUID>,
        authmetaRepo: AuthMetadataRepository<UUID>
    ) : CassandraPersistenceServices<UUID>(keyService, userRepo, topicRepo, messageRepo, membershipRepo, authmetaRepo) {
        @Configuration
        class PersistenceControllers : PersistenceControllersConfiguration()
    }

    @Bean       // Ideally, this is a (64+bits) partition mask value
    @ConditionalOnProperty("app.service.core.key", havingValue = "long")
    open fun longKeyGen(): IKeyGenerator<Long> = AtomicLongKeyGenerator(abs(Random.nextLong()))

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