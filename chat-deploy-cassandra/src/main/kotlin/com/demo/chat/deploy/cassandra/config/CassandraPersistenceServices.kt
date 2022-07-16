package com.demo.chat.deploy.cassandra.config

import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.domain.AuthMetadata
import com.demo.chat.repository.cassandra.*
import com.demo.chat.service.*
import com.demo.chat.service.dummy.DummyPersistenceStore
import com.demo.chat.service.persistence.*
import com.demo.chat.service.security.AuthMetaPersistence

open class CassandraPersistenceServices<T>(
    private val keyService: IKeyService<T>,
    private val userRepo: ChatUserRepository<T>,
    private val topicRepo: TopicRepository<T>,
    private val messageRepo: ChatMessageRepository<T>,
    private val membershipRepo: TopicMembershipRepository<T>,
    private val authMetadataRepo: AuthMetadataRepository<T>
) : PersistenceServiceBeans<T, String> {

    override fun user(): UserPersistence<T> =
        UserPersistenceCassandra(keyService, userRepo)

    override fun topic(): TopicPersistence<T> =
        TopicPersistenceCassandra(keyService, topicRepo)

    override fun message(): MessagePersistence<T, String> =
        MessagePersistenceCassandra(keyService, messageRepo)

    override fun membership(): MembershipPersistence<T> =
        MembershipPersistenceCassandra(keyService, membershipRepo)

    override fun authMetadata(): AuthMetaPersistence<T> =
        AuthMetaPersistenceCassandra(keyService, authMetadataRepo)
}