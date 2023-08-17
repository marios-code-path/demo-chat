package com.demo.chat.persistence.cassandra

import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.persistence.cassandra.impl.*
import com.demo.chat.persistence.cassandra.repository.*
import com.demo.chat.service.core.*
import com.demo.chat.service.security.AuthMetaPersistence
import com.fasterxml.jackson.databind.ObjectMapper

open class CassandraPersistenceServices<T>(
    private val keyService: IKeyService<T>,
    private val userRepo: ChatUserRepository<T>,
    private val topicRepo: TopicRepository<T>,
    private val messageRepo: ChatMessageRepository<T>,
    private val membershipRepo: TopicMembershipRepository<T>,
    private val authMetadataRepo: AuthMetadataRepository<T>,
    private val keyValueRepo: KeyValuePairRepository<T>,
    private val mapper: ObjectMapper = ObjectMapper()
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

    override fun keyValuePersistence(): KeyValueStore<T, Any> =
        KeyValuePersistenceCassandra(keyService, keyValueRepo, mapper)
}