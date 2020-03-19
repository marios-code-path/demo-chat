package com.demo.deploy.config

import com.demo.chat.repository.cassandra.*
import com.demo.chat.service.MembershipIndexService
import com.demo.chat.service.MessageIndexService
import com.demo.chat.service.TopicIndexService
import com.demo.chat.service.UserIndexService
import com.demo.chat.service.index.*
import org.springframework.context.annotation.Bean
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate

open class CassandraIndexFactory<T>(
        private val cassandra: ReactiveCassandraTemplate,
        private val userHandleRepo: ChatUserHandleRepository<T>,
        private val roomRepo: TopicRepository<T>,
        private val nameRepo: TopicByNameRepository<T>,
        private val byMemberRepo: TopicMembershipByMemberRepository<T>,
        private val byMemberOfRepo: TopicMembershipByMemberOfRepository<T>,
        private val byUserRepo: ChatMessageByUserRepository<T>,
        private val byTopicRepo: ChatMessageByTopicRepository<T>
) {
    open fun userIndex(): UserIndexService<T> =
            UserIndexCassandra(UserCriteriaCodec(), userHandleRepo, cassandra)

    open fun roomIndex(): TopicIndexService<T> =
            TopicIndexCassandra(TopicCriteriaCodec(), roomRepo, nameRepo)

    open fun membershipIndex(): MembershipIndexService<T> =
            MembershipIndexCassandra(MembershipCriteriaCodec(), byMemberRepo, byMemberOfRepo)

    open fun messageIndex(): MessageIndexService<T, String> =
            MessageIndexCassandra(MessageCriteriaCodec(), byUserRepo, byTopicRepo)
}