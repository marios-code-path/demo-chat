package com.demo.chat.config

import com.demo.chat.ExcludeFromTests
import com.demo.chat.repository.cassandra.*
import com.demo.chat.service.*
import com.demo.chat.service.persistence.ChatRoomPersistenceCassandra
import com.demo.chat.service.persistence.ChatUserPersistenceCassandra
import com.demo.chat.service.persistence.KeyPersistenceCassandra
import com.demo.chat.service.persistence.TextMessagePersistenceCassandra
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate

@ConfigurationProperties("sample")
data class SampleProps(val name:String)

@Profile("cassandra-persistence")
@ConfigurationProperties("cassandra-repo")
data class CassandraProperties(override val contactPoints: String,
                               override val port: Int,
                               override val keyspace: String,
                               override val basePackages: String,
                               override val jmxReporting: Boolean) : ConfigurationPropertiesCassandra

@ExcludeFromTests
@Profile("cassandra-persistence")
@Configuration
class PersistenceConfiguration {

    @Bean
    fun cluster(props: ConfigurationPropertiesCassandra) = ClusterConfigurationCassandra(props)

    @Bean
    fun keyService(template: ReactiveCassandraTemplate): KeyService = KeyPersistenceCassandra(template)

    @Bean
    fun userPersistence(keyService: KeyService,
                        userRepo: ChatUserRepository,
                        userHandleRepo: ChatUserHandleRepository): ChatUserPersistence =
            ChatUserPersistenceCassandra(keyService, userRepo)

    @Bean
    fun roomPersistence(keyService: KeyService,
                        roomRepo: ChatRoomRepository,
                        roomNameRepo: ChatRoomNameRepository): ChatRoomPersistence =
            ChatRoomPersistenceCassandra(keyService, roomRepo, roomNameRepo)

    @Bean
    fun messagePersistence(keyService: KeyService,
                           messageRepo: ChatMessageRepository,
                           messageByTopicRepo: ChatMessageByTopicRepository): TextMessagePersistence =
            TextMessagePersistenceCassandra(keyService, messageRepo)
}