package com.demo.chat.index.cassandra.config

import com.demo.chat.config.IndexServiceBeans
import com.demo.chat.domain.TypeUtil
import com.demo.chat.index.cassandra.impl.*
import com.demo.chat.index.cassandra.repository.*
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate


open class CassandraIndexServices<T>(
    private val cassandra: ReactiveCassandraTemplate,
    private val userHandleRepo: ChatUserHandleRepository<T>,
    private val nameRepo: TopicByNameRepository<T>,
    private val byMemberRepo: TopicMembershipByMemberRepository<T>,
    private val byMemberOfRepo: TopicMembershipByMemberOfRepository<T>,
    private val byUserRepo: ChatMessageByUserRepository<T>,
    private val byTopicRepo: ChatMessageByTopicRepository<T>,
    private val principalRepo: AuthMetadataByPrincipalRepository<T>,
    private val targetRepo: AuthMetadataByTargetRepository<T>,
    private val typeUtil: TypeUtil<T>
) : IndexServiceBeans<T, String, Map<String, String>> {
    override fun userIndex() = UserIndex(userHandleRepo, cassandra)

    override fun topicIndex() = TopicIndex(nameRepo)

    override fun membershipIndex() = MembershipIndex(typeUtil::fromString, byMemberRepo, byMemberOfRepo)

    override fun messageIndex() = MessageIndex(typeUtil::fromString, byUserRepo, byTopicRepo)

    override fun authMetadataIndex() = AuthMetadataIndex(typeUtil, targetRepo, principalRepo)
}