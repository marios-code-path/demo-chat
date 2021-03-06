package com.demo.chat.deploy.config

import com.demo.chat.deploy.config.core.PersistenceServiceConfiguration
import com.demo.chat.repository.cassandra.ChatMessageRepository
import com.demo.chat.repository.cassandra.ChatUserRepository
import com.demo.chat.repository.cassandra.TopicMembershipRepository
import com.demo.chat.repository.cassandra.TopicRepository
import com.demo.chat.service.*
import com.demo.chat.service.persistence.*

open class CassandraPersistenceServiceConfiguration<T>(
        private val keyService: IKeyService<T>,
        private val userRepo: ChatUserRepository<T>,
        private val topicRepo: TopicRepository<T>,
        private val messageRepo: ChatMessageRepository<T>,
        private val membershipRepo: TopicMembershipRepository<T>,
)
    : PersistenceServiceConfiguration<T, String> {

    override fun user(): UserPersistence<T> =
            UserPersistenceCassandra(keyService, userRepo)

    override fun topic(): TopicPersistence<T> =
            TopicPersistenceCassandra(keyService, topicRepo)

    override fun message(): MessagePersistence<T, String> =
            MessagePersistenceCassandra(keyService, messageRepo)

    override fun membership(): MembershipPersistence<T> =
            MembershipPersistenceCassandra(keyService, membershipRepo)
}