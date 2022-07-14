package com.demo.chat.deploy.cassandra.config

import com.datastax.oss.driver.api.core.uuid.Uuids
import com.demo.chat.config.KeyServiceBeans
import com.demo.chat.controller.config.IndexControllersConfiguration
import com.demo.chat.controller.config.KeyControllersConfiguration
import com.demo.chat.controller.config.PersistenceControllersConfiguration
import com.demo.chat.repository.cassandra.*
import com.demo.chat.service.IKeyService
import com.demo.chat.service.persistence.KeyServiceCassandra
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import java.util.*

class CassandraAppConfiguration {
    // TODO: USE of CoreServiceBeans
    // TODO: see chat-deploy-client-consul
    @Configuration
    @ConditionalOnProperty(prefix = "app.service.core", name = ["index"])
    class IndexConfiguration {
        @Configuration
        class CassandraBackedIndexBeans(
            cassandra: ReactiveCassandraTemplate,
            userHandleRepo: ChatUserHandleRepository<UUID>,
            roomRepo: TopicRepository<UUID>,
            nameRepo: TopicByNameRepository<UUID>,
            byMemberRepo: TopicMembershipByMemberRepository<UUID>,
            byMemberOfRepo: TopicMembershipByMemberOfRepository<UUID>,
            byUserRepo: ChatMessageByUserRepository<UUID>,
            byTopicRepo: ChatMessageByTopicRepository<UUID>,
        ) : CassandraIndexServiceConfiguration<UUID>(
            cassandra,
            userHandleRepo,
            roomRepo,
            nameRepo,
            byMemberRepo,
            byMemberOfRepo,
            byUserRepo,
            byTopicRepo,
            UUID::fromString
        ) {
            @Configuration
            class AppIndexControllers : IndexControllersConfiguration()
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "app.service.core", name = ["persistence"])
    class PersistenceConfiguration {
        @Configuration
        class PersistenceConfiguration(
            keyService: IKeyService<UUID>,
            userRepo: ChatUserRepository<UUID>,
            topicRepo: TopicRepository<UUID>,
            messageRepo: ChatMessageRepository<UUID>,
            membershipRepo: TopicMembershipRepository<UUID>,
        ) : CassandraPersistenceServiceConfiguration<UUID>(keyService, userRepo, topicRepo, messageRepo, membershipRepo)

        @Configuration
        class AppPersistenceControllerConfiguration : PersistenceControllersConfiguration()
    }

    @Configuration
    @ConditionalOnProperty(prefix = "app.service.core", name = ["key"])
    class KeyConfiguration {
        @Configuration
        class CassandraKeyBeans(val t: ReactiveCassandraTemplate) : KeyServiceBeans<UUID> {
            override fun keyService(): IKeyService<UUID> = KeyServiceCassandra(t, Uuids::timeBased)
        }

        @Configuration
        class KeyControllers : KeyControllersConfiguration()
    }
}