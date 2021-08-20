package com.demo.chat.deploy.config

import com.demo.chat.config.IndexServiceBeans
import com.demo.chat.repository.cassandra.*
import com.demo.chat.service.index.MembershipIndexCassandra
import com.demo.chat.service.index.MessageIndexCassandra
import com.demo.chat.service.index.TopicIndexCassandra
import com.demo.chat.service.index.UserIndexCassandra
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import java.util.function.Function


open class CassandraIndexServiceConfiguration<T>(
        private val cassandra: ReactiveCassandraTemplate,
        private val userHandleRepo: ChatUserHandleRepository<T>,
        private val roomRepo: TopicRepository<T>,
        private val nameRepo: TopicByNameRepository<T>,
        private val byMemberRepo: TopicMembershipByMemberRepository<T>,
        private val byMemberOfRepo: TopicMembershipByMemberOfRepository<T>,
        private val byUserRepo: ChatMessageByUserRepository<T>,
        private val byTopicRepo: ChatMessageByTopicRepository<T>,
        private val stringToKeyCodec: Function<String, T>,
) : IndexServiceBeans<T, String, Map<String, String>> {
    override fun userIndex() =
            UserIndexCassandra(userHandleRepo, cassandra)

    override fun topicIndex() =
            TopicIndexCassandra(roomRepo, nameRepo)

    override fun membershipIndex() =
            MembershipIndexCassandra(stringToKeyCodec, byMemberRepo, byMemberOfRepo)

    override fun messageIndex() =
            MessageIndexCassandra(stringToKeyCodec, byUserRepo, byTopicRepo)
}