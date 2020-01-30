package com.demo.chat.config

import com.datastax.driver.core.utils.UUIDs
import com.demo.chat.codec.Codec
import com.demo.chat.repository.cassandra.ChatMessageRepository
import com.demo.chat.repository.cassandra.ChatUserRepository
import com.demo.chat.repository.cassandra.TopicMembershipRepository
import com.demo.chat.repository.cassandra.TopicRepository
import com.demo.chat.service.*
import com.demo.chat.service.persistence.*
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.context.annotation.Bean
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import java.util.*

class UUIDKeyGeneratorCassandra : Codec<Unit, UUID> {
    override fun decode(record: Unit): UUID {
        return UUIDs.timeBased()
    }
}

open class CassandraConfiguration(private val cassandraProps: ConfigurationPropertiesCassandra) {
    @Bean
    fun cluster() = ClusterConfigurationCassandra(cassandraProps)
}

open class KeyPersistenceConfigurationCassandra<T>(
        private val template: ReactiveCassandraTemplate,
        private val keyGenerator: Codec<Unit, T>) {
    @Bean
    fun keyPersistence(): IKeyService<T> =
            KeyServiceCassandra(template, keyGenerator)
}

open class PersistenceConfigurationCassandra<T>(
        private val keyService: IKeyService<T>,
        private val userRepo: ChatUserRepository<T>,
        private val topicRepo: TopicRepository<T>,
        private val messageRepo: ChatMessageRepository<T>,
        private val membershipRepo: TopicMembershipRepository<T>) {

    @Bean
    fun userPersistence(): UserPersistence<T> =
            UserPersistenceCassandra(keyService, userRepo)

    @Bean
    fun topicPersistence(): TopicPersistence<T> =
            TopicPersistenceCassandra(keyService, topicRepo)

    @Bean
    fun messagePersistence(): MessagePersistence<T, String> =
            MessagePersistenceCassandra(keyService, messageRepo)

    @Bean
    fun membershipPersistence(): MembershipPersistence<T> =
            MembershipPersistenceCassandra(keyService, membershipRepo)
}