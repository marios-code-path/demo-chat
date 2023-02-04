package com.demo.chat.index.cassandra.config

import com.demo.chat.domain.TypeUtil
import com.demo.chat.index.cassandra.repository.*
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate

@Configuration
@ConditionalOnProperty(prefix = "app.service.core", name = ["index"])
class IndexServiceConfiguration<T> (
    cassandra: ReactiveCassandraTemplate,
    userHandleRepo: ChatUserHandleRepository<T>,
    nameRepo: TopicByNameRepository<T>,
    byMemberRepo: TopicMembershipByMemberRepository<T>,
    byMemberOfRepo: TopicMembershipByMemberOfRepository<T>,
    byUserRepo: ChatMessageByUserRepository<T>,
    byTopicRepo: ChatMessageByTopicRepository<T>,
    principalRepo: AuthMetadataByPrincipalRepository<T>,
    targetRepo: AuthMetadataByTargetRepository<T>,
    typeUtil: TypeUtil<T>
    ) : CassandraIndexServices<T>(
        cassandra,
        userHandleRepo,
        nameRepo,
        byMemberRepo,
        byMemberOfRepo,
        byUserRepo,
        byTopicRepo,
        principalRepo,
        targetRepo,
        typeUtil
    )