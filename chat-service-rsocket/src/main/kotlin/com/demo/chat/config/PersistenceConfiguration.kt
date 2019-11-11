package com.demo.chat.config

import com.demo.chat.ExcludeFromTests
import com.demo.chat.repository.cassandra.*
import com.demo.chat.service.*
import com.demo.chat.service.index.ChatMessageIndexCassandra
import com.demo.chat.service.index.ChatUserIndexCassandra
import com.demo.chat.service.index.RoomIndexCassandra
import com.demo.chat.service.persistence.ChatRoomPersistenceCassandra
import com.demo.chat.service.persistence.ChatUserPersistenceCassandra
import com.demo.chat.service.persistence.KeyServiceCassandra
import com.demo.chat.service.persistence.TextMessagePersistenceCassandra
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import org.springframework.stereotype.Component
import java.beans.ConstructorProperties

@ConstructorBinding
@ConfigurationProperties("cassandra-repo")
data class CassandraProperties(override val contactPoints: String,
                               override val port: Int,
                               override val keyspace: String,
                               override val basePackages: String,
                               override val jmxReporting: Boolean) : ConfigurationPropertiesCassandra

@ExcludeFromTests
@Profile("cassandra-index")
@Configuration
class IndexConfiguration {
    @Bean
    fun userIndex(userHandleRepo: ChatUserHandleRepository,
                  cassandra: ReactiveCassandraTemplate): ChatUserIndexService = ChatUserIndexCassandra(userHandleRepo, cassandra)

    @Bean
    fun roomIndex(roomRepo: ChatRoomRepository,
                  nameRepo: ChatRoomNameRepository): ChatRoomIndexService = RoomIndexCassandra(roomRepo, nameRepo)

    @Bean
    fun messageIndex(cassandra: ReactiveCassandraTemplate,
                     byUserRepo: ChatMessageByUserRepository,
                     byTopicRepo: ChatMessageByTopicRepository): ChatMessageIndexService =
            ChatMessageIndexCassandra(cassandra, byUserRepo, byTopicRepo)
}

@ExcludeFromTests
@Profile("cassandra-persistence")
@Configuration
class PersistenceConfiguration {
    @Bean
    fun cluster(props: ConfigurationPropertiesCassandra) = ClusterConfigurationCassandra(props)

    @Bean
    fun keyService(template: ReactiveCassandraTemplate): KeyService = KeyServiceCassandra(template)

    @Bean
    fun userPersistence(keyService: KeyService,
                        userRepo: ChatUserRepository): ChatUserPersistence =
            ChatUserPersistenceCassandra(keyService, userRepo)

    @Bean
    fun roomPersistence(keyService: KeyService,
                        roomRepo: ChatRoomRepository,
                        roomNameRepo: ChatRoomNameRepository): ChatRoomPersistence =
            ChatRoomPersistenceCassandra(keyService, roomRepo)

    @Bean
    fun messagePersistence(keyService: KeyService,
                           messageRepo: ChatMessageRepository): TextMessagePersistence =
            TextMessagePersistenceCassandra(keyService, messageRepo)
}