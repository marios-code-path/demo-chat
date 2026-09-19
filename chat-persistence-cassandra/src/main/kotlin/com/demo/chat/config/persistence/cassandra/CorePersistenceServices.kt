package com.demo.chat.config.persistence.cassandra

import com.demo.chat.persistence.cassandra.CassandraPersistenceServices
import com.demo.chat.persistence.cassandra.repository.*
import com.demo.chat.service.core.IKeyService
import com.demo.chat.config.JACKSON_2_OBJECT_MAPPER
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(prefix = "app.service.core", name = ["persistence"], havingValue = "cassandra")
class CorePersistenceServices<T : Any>(
    keyService: IKeyService<T>,
    userRepo: ChatUserRepository<T>,
    topicRepo: TopicRepository<T>,
    messageRepo: ChatMessageRepository<T>,
    membershipRepo: TopicMembershipRepository<T>,
    authmetaRepo: AuthMetadataRepository<T>,
    keyValueRepo: KeyValuePairRepository<T>,
    @Qualifier(JACKSON_2_OBJECT_MAPPER) mapper: ObjectMapper
) : CassandraPersistenceServices<T>(keyService, userRepo, topicRepo,
    messageRepo, membershipRepo, authmetaRepo, keyValueRepo, mapper)