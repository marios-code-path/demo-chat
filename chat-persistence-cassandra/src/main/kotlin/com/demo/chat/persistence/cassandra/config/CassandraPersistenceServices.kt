package com.demo.chat.persistence.cassandra.config

import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.persistence.cassandra.impl.*
import com.demo.chat.persistence.cassandra.repository.*
import com.demo.chat.persistence.impl.cassandra.*
import com.demo.chat.persistence.repository.cassandra.*
import com.demo.chat.service.*
import com.demo.chat.service.security.AuthMetaPersistence

open class CassandraPersistenceServices<T>(
    private val keyService: IKeyService<T>,
    private val userRepo: ChatUserRepository<T>,
    private val topicRepo: TopicRepository<T>,
    private val messageRepo: ChatMessageRepository<T>,
    private val membershipRepo: TopicMembershipRepository<T>,
    private val authMetadataRepo: AuthMetadataRepository<T>
) : PersistenceServiceBeans<T, String> {

    override fun userPersistence(): UserPersistence<T> =
        UserPersistenceCassandra(keyService, userRepo)

    override fun topicPersistence(): TopicPersistence<T> =
        TopicPersistenceCassandra(keyService, topicRepo)

    override fun messagePersistence(): MessagePersistence<T, String> =
        MessagePersistenceCassandra(keyService, messageRepo)

    override fun membershipPersistence(): MembershipPersistence<T> =
        MembershipPersistenceCassandra(keyService, membershipRepo)

    override fun authMetaPersistence(): AuthMetaPersistence<T> =
        AuthMetaPersistenceCassandra(keyService, authMetadataRepo)
}