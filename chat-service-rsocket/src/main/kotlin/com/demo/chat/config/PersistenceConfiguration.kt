package com.demo.chat.config

import com.demo.chat.domain.*
import com.demo.chat.repository.cassandra.*
import com.demo.chat.service.*
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/**
 *     @Bean
fun cassandraProperties(): ConfigurationPropertiesCassandra = CassandraProperties("127.0.0.1",
9142,
"chat",
"com.demo.chat.repository.cassandra")
 */
@Profile("cassandra-persistence")
@ConfigurationProperties("cassandra-repo")
data class CassandraProperties(override val contactPoints: String = "127.0.0.1",
                               override val port: Int = 9042,
                               override val keyspace: String = "chat",
                               override val basePackages: String = "com.demo.chat.repository.cassandra") : ConfigurationPropertiesCassandra


@Profile("cassandra-persistence")
@Configuration
class PersistenceConfiguration {

    @Profile("cassandra-persistence")
    @Configuration
    class CassandraCluster(props: ConfigurationPropertiesCassandra) : ClusterConfigurationCassandra(props)

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