package com.demo.chat.persistence.cassandra.config

import com.demo.chat.persistence.cassandra.CassandraPersistenceServices
import com.demo.chat.persistence.cassandra.repository.*
import com.demo.chat.service.core.IKeyService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(prefix = "app.service.core", name = ["persistence"])
class CorePersistenceServices<T>(
    keyService: IKeyService<T>,
    userRepo: ChatUserRepository<T>,
    topicRepo: TopicRepository<T>,
    messageRepo: ChatMessageRepository<T>,
    membershipRepo: TopicMembershipRepository<T>,
    authmetaRepo: AuthMetadataRepository<T>
) : CassandraPersistenceServices<T>(keyService, userRepo, topicRepo, messageRepo, membershipRepo, authmetaRepo) {


}