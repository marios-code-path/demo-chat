package com.demo.deploy.config

import com.datastax.driver.core.utils.UUIDs
import com.demo.chat.codec.Codec
import com.demo.chat.config.ClusterConfigurationCassandra
import com.demo.chat.config.ConfigurationPropertiesCassandra
import com.demo.chat.repository.cassandra.ChatMessageRepository
import com.demo.chat.repository.cassandra.ChatUserRepository
import com.demo.chat.repository.cassandra.TopicMembershipRepository
import com.demo.chat.repository.cassandra.TopicRepository
import com.demo.chat.service.*
import com.demo.chat.service.persistence.*
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Profile
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import java.util.*

class UUIDKeyGeneratorCassandra : Codec<Unit, UUID> {
    override fun decode(record: Unit): UUID {
        return UUIDs.timeBased()
    }
}

open class CassandraConfiguration(private val cassandraProps: ConfigurationPropertiesCassandra) {
    @Bean
    open fun cluster() = ClusterConfigurationCassandra(cassandraProps)
}

open class KeyServiceConfigurationCassandra<T>(
        private val template: ReactiveCassandraTemplate,
        private val keyGenerator: Codec<Unit, T>) {
    open fun keyService(): IKeyService<T> =
            KeyServiceCassandra(template, keyGenerator)
}

open class PersistenceConfigurationCassandra<T>(
        private val keyService: IKeyService<T>,
        private val userRepo: ChatUserRepository<T>,
        private val topicRepo: TopicRepository<T>,
        private val messageRepo: ChatMessageRepository<T>,
        private val membershipRepo: TopicMembershipRepository<T>) {

    @Profile("cassandra-user")
    @Bean
    open fun userPersistence(): UserPersistence<T> =
            UserPersistenceCassandra(keyService, userRepo)

    @Profile("cassandra-topic")
    @Bean
    open fun topicPersistence(): TopicPersistence<T> =
            TopicPersistenceCassandra(keyService, topicRepo)

    @Profile("cassandra-message")
    @Bean
    open fun messagePersistence(): MessagePersistence<T, String> =
            MessagePersistenceCassandra(keyService, messageRepo)

    @Profile("cassandra-membership")
    @Bean
    open fun membershipPersistence(): MembershipPersistence<T> =
            MembershipPersistenceCassandra(keyService, membershipRepo)
}