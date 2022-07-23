package com.demo.chat.deploy.cassandra.config

import com.demo.chat.config.IndexServiceBeans
import com.demo.chat.domain.TypeUtil
import com.demo.chat.repository.cassandra.*
import com.demo.chat.service.index.*
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate


open class CassandraIndexServices<T>(
    private val cassandra: ReactiveCassandraTemplate,
    private val userHandleRepo: ChatUserHandleRepository<T>,
    private val roomRepo: TopicRepository<T>,
    private val nameRepo: TopicByNameRepository<T>,
    private val byMemberRepo: TopicMembershipByMemberRepository<T>,
    private val byMemberOfRepo: TopicMembershipByMemberOfRepository<T>,
    private val byUserRepo: ChatMessageByUserRepository<T>,
    private val byTopicRepo: ChatMessageByTopicRepository<T>,
    private val principalRepo: AuthMetadataByPrincipalRepository<T>,
    private val targetRepo: AuthMetadataByTargetRepository<T>,
    private val typeUtil: TypeUtil<T>
) : IndexServiceBeans<T, String, Map<String, String>> {
    override fun userIndex() = UserIndexCassandra(userHandleRepo, cassandra)

    override fun topicIndex() = TopicIndexCassandra(nameRepo)

    override fun membershipIndex() = MembershipIndexCassandra(typeUtil::fromString, byMemberRepo, byMemberOfRepo)

    override fun messageIndex() = MessageIndexCassandra(typeUtil::fromString, byUserRepo, byTopicRepo)

    override fun authMetadataIndex() = AuthMetadataIndexCassandra(typeUtil, targetRepo, principalRepo)
}