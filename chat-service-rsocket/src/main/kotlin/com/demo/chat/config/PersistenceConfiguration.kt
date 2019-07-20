package com.demo.chat.config

import com.demo.chat.ExcludeFromTests
import com.demo.chat.domain.*
import com.demo.chat.repository.cassandra.*
import com.demo.chat.service.*
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile


@Profile("cassandra-persistence")
@ConfigurationProperties("cassandra-repo")
data class CassandraProperties(override val contactPoints: String,
                               override val port: Int,
                               override val keyspace: String,
                               override val basePackages: String) : ConfigurationPropertiesCassandra


@Profile("cassandra-persistence")
@Configuration
class PersistenceConfiguration {

    @Profile("cassandra-persistence")
    @Configuration
    class CassandraCluster(props : ConfigurationPropertiesCassandra) : ClusterConfigurationCassandra(props)

    @Bean
    fun keyService(): KeyService = KeyServiceCassandra

    @Bean
    fun userPersistence(keyService: KeyService,
                        userRepo: ChatUserRepository,
                        userHandleRepo: ChatUserHandleRepository): ChatUserPersistence<out User, UserKey> =
            ChatUserPersistenceCassandra(keyService, userRepo, userHandleRepo)

    @Bean
    fun roomPersistence(keyService: KeyService,
                        roomRepo: ChatRoomRepository,
                        roomNameRepo: ChatRoomNameRepository): ChatRoomPersistence<out Room, RoomKey> =
            ChatRoomPersistenceCassandra(keyService, roomRepo, roomNameRepo)

    @Bean
    fun messagePersistence(keyService: KeyService,
                           messageRepo: ChatMessageRepository,
                           messageByTopicRepo: ChatMessageByTopicRepository): TextMessagePersistence<out TextMessage, TextMessageKey> =
            TextMessagePersistenceCassandra(keyService, messageRepo, messageByTopicRepo)
}