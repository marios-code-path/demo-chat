package com.demo.deploy.config

import com.datastax.driver.core.utils.UUIDs
import com.demo.chat.codec.Codec
import com.demo.chat.repository.cassandra.ChatMessageRepository
import com.demo.chat.repository.cassandra.ChatUserRepository
import com.demo.chat.repository.cassandra.TopicMembershipRepository
import com.demo.chat.repository.cassandra.TopicRepository
import com.demo.chat.service.*
import com.demo.chat.service.persistence.*
import org.springframework.context.annotation.Bean
import java.util.*

class UUIDKeyGeneratorCassandra : Codec<Unit, UUID> {
    override fun decode(record: Unit): UUID {
        return UUIDs.timeBased()
    }
}

open class CassandraPersistenceFactory<T>(
        private val keyService: IKeyService<T>,
        private val userRepo: ChatUserRepository<T>,
        private val topicRepo: TopicRepository<T>,
        private val messageRepo: ChatMessageRepository<T>,
        private val membershipRepo: TopicMembershipRepository<T>) {
    @Bean
    open fun userPersistence(): UserPersistence<T> =
            UserPersistenceCassandra(keyService, userRepo)
    @Bean
    open fun topicPersistence(): TopicPersistence<T> =
            TopicPersistenceCassandra(keyService, topicRepo)
    @Bean
    open fun messagePersistence(): MessagePersistence<T, String> =
            MessagePersistenceCassandra(keyService, messageRepo)
    @Bean
    open fun membershipPersistence(): MembershipPersistence<T> =
            MembershipPersistenceCassandra(keyService, membershipRepo)
}