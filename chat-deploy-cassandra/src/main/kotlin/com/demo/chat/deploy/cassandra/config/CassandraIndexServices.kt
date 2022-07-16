package com.demo.chat.deploy.cassandra.config

import com.demo.chat.config.IndexServiceBeans
import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.TypeUtil
import com.demo.chat.repository.cassandra.*
import com.demo.chat.service.DummyAuthMetaIndex
import com.demo.chat.service.IndexService
import com.demo.chat.service.dummy.DummyIndexService
import com.demo.chat.service.index.*
import com.demo.chat.service.security.AuthMetaIndex
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import java.util.function.Function


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
    private val stringToKeyCodec: Function<String, T>,
    private val typeUtil: TypeUtil<T>
) : IndexServiceBeans<T, String, Map<String, String>> {
    override fun userIndex() = UserIndexCassandra(userHandleRepo, cassandra)

    override fun topicIndex() = TopicIndexCassandra(nameRepo)

    override fun membershipIndex() = MembershipIndexCassandra(stringToKeyCodec, byMemberRepo, byMemberOfRepo)

    override fun messageIndex() = MessageIndexCassandra(stringToKeyCodec, byUserRepo, byTopicRepo)

    override fun authMetadataIndex() = AuthMetadataIndexCassandra(typeUtil, targetRepo, principalRepo)
}