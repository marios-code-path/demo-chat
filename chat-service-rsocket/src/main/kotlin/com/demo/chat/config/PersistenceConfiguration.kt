package com.demo.chat.config

import com.datastax.driver.core.utils.UUIDs
import com.demo.chat.ExcludeFromTests
import com.demo.chat.repository.cassandra.*
import com.demo.chat.service.*
import com.demo.chat.service.index.*
import com.demo.chat.service.persistence.*
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import java.util.*

@ConstructorBinding
@ConfigurationProperties("cassandra-repo")
data class CassandraProperties(override val contactPoints: String,
                               override val port: Int,
                               override val keyspace: String,
                               override val basePackages: String,
                               override val jmxReporting: Boolean) : ConfigurationPropertiesCassandra

@ExcludeFromTests
@Profile("cassandra-persistence")
@Configuration
class PersistenceConfiguration<T : UUID> {

    @Bean
    fun cluster(props: ConfigurationPropertiesCassandra) = ClusterConfigurationCassandra(props)

    @Bean
    fun keyService(template: ReactiveCassandraTemplate): IKeyService<T> = KeyServiceCassandra(template) { UUIDs.timeBased() as T }

    @Bean
    fun userPersistence(keyService: IKeyService<T>,
                        userRepo: ChatUserRepository<T>): UserPersistence<T> =
            UserPersistenceCassandra(keyService, userRepo)

    @Bean
    fun roomPersistence(keyService: IKeyService<T>,
                        roomRepo: TopicRepository<T>,
                        roomNameRepo: TopicByNameRepository<T>): TopicPersistence<T> =
            TopicPersistenceCassandra(keyService, roomRepo)

    @Bean
    fun messagePersistence(keyService: IKeyService<T>,
                           messageRepo: ChatMessageRepository<T>): TextMessagePersistence<T> =
            TextMessagePersistenceCassandra(keyService, messageRepo)

    @Bean
    fun membershipPersistence(keyService: IKeyService<T>,
                              membershipRepo: ChatMembershipRepository<T>): MembershipPersistence<T> =
            MembershipPersistenceCassandra(keyService, membershipRepo)
}