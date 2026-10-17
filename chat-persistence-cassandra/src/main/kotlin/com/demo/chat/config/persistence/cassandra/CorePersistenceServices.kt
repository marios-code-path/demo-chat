package com.demo.chat.config.persistence.cassandra

import org.springframework.context.annotation.Bean

import com.demo.chat.persistence.cassandra.impl.CassandraStoreShapeCheck

import com.demo.chat.service.core.StoreShapeCheck

import com.datastax.oss.driver.api.core.CqlSession
import com.demo.chat.domain.TypeUtil

import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.persistence.cassandra.CassandraPersistenceServices
import com.demo.chat.persistence.cassandra.repository.*
import com.demo.chat.service.core.IKeyService
import com.demo.chat.config.JACKSON_2_OBJECT_MAPPER
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import com.demo.chat.service.core.UserPersistence
import com.demo.chat.service.core.TopicPersistence
import com.demo.chat.service.core.MessagePersistence
import com.demo.chat.service.core.MembershipPersistence
import com.demo.chat.service.core.KeyValueStore
import com.demo.chat.service.security.AuthMetaPersistence
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(prefix = "app.service.core", name = ["persistence"], havingValue = "cassandra")
class CorePersistenceServices<T : Any>(
    keyService: IKeyService<T>,
    rootKeys: RootKeys<T>,
    userRepo: ChatUserRepository<T>,
    topicRepo: TopicRepository<T>,
    messageRepo: ChatMessageRepository<T>,
    membershipRepo: TopicMembershipRepository<T>,
    authmetaRepo: AuthMetadataRepository<T>,
    keyValueRepo: KeyValuePairRepository<T>,
    typeUtil: TypeUtil<T>,
    @Qualifier(JACKSON_2_OBJECT_MAPPER) mapper: ObjectMapper
) : CassandraPersistenceServices<T>(keyService, rootKeys, userRepo, topicRepo,
    messageRepo, membershipRepo, authmetaRepo, keyValueRepo, typeUtil, mapper) {

    // Each store service is a bean, as on Redis. A call through this
    // configuration then returns the bean, and the store write gate wraps it.
    // Before CHAT-lswjobhz each call built a new service, and no service was
    // a bean.

    @Bean
    override fun userPersistence(): UserPersistence<T> = super.userPersistence()

    @Bean
    override fun topicPersistence(): TopicPersistence<T> = super.topicPersistence()

    @Bean
    override fun messagePersistence(): MessagePersistence<T, String> = super.messagePersistence()

    @Bean
    override fun membershipPersistence(): MembershipPersistence<T> = super.membershipPersistence()

    @Bean
    override fun authMetaPersistence(): AuthMetaPersistence<T> = super.authMetaPersistence()

    @Bean
    override fun keyValuePersistence(): KeyValueStore<T, Any> = super.keyValuePersistence()

    /**
     * The shape of the persistence tables. It runs before the root keys load,
     * also when another backend provides the keys. See `CHAT-avduuqwp`, T7.
     */
    @Bean
    fun cassandraPersistenceShapeCheck(session: CqlSession, typeUtil: TypeUtil<*>): StoreShapeCheck =
        CassandraStoreShapeCheck.lazy(
            session,
            CassandraStoreShapeCheck.PERSISTENCE_TABLES,
            CassandraStoreShapeCheck.persistenceTypes(typeUtil),
        )
}
