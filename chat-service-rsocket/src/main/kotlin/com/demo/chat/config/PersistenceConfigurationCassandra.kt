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

@ConstructorBinding
@ConfigurationProperties("cassandra-repo")
data class CassandraProperties(override val contactPoints: String,
                               override val port: Int,
                               override val keyspace: String,
                               override val basePackages: String,
                               override val jmxReporting: Boolean) : ConfigurationPropertiesCassandra

class UUIDKeyGeneratorCassandra : Codec<Unit, UUID> {
    override fun decode(record: Unit): UUID {
        return UUIDs.timeBased()
    }
}

open class PersistenceConfigurationCassandra<T>(private val keyGenerator: Codec<Unit, T>) {
    @Bean
    fun cluster(props: ConfigurationPropertiesCassandra) = ClusterConfigurationCassandra(props)

    @Bean
    fun keyPersistence(template: ReactiveCassandraTemplate): IKeyService<T> =
            KeyServiceCassandra(template, keyGenerator)

    @Bean
    fun userPersistence(keyService: IKeyService<T>,
                        userRepo: ChatUserRepository<T>): UserPersistence<T> =
            UserPersistenceCassandra(keyService, userRepo)

    @Bean
    fun topicPersistence(keyService: IKeyService<T>,
                         topicRepo: TopicRepository<T>): TopicPersistence<T> =
            TopicPersistenceCassandra(keyService, topicRepo)

    @Bean
    fun messagePersistence(keyService: IKeyService<T>,
                           messageRepo: ChatMessageRepository<T>): MessagePersistence<T, String> =
            MessagePersistenceCassandra(keyService, messageRepo)

    @Bean
    fun membershipPersistence(keyService: IKeyService<T>,
                              membershipRepo: TopicMembershipRepository<T>): MembershipPersistence<T> =
            MembershipPersistenceCassandra(keyService, membershipRepo)
}

@Suppress("unused")
class PersistenceCassandraUUIDKey:
        PersistenceConfigurationCassandra<UUID>(UUIDKeyGeneratorCassandra())