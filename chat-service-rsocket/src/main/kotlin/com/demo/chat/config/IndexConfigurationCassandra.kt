package com.demo.chat.config

import com.demo.chat.repository.cassandra.*
import com.demo.chat.service.MembershipIndexService
import com.demo.chat.service.MessageIndexService
import com.demo.chat.service.TopicIndexService
import com.demo.chat.service.UserIndexService
import com.demo.chat.service.index.*
import org.springframework.context.annotation.Bean
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import java.util.*


open class IndexConfigurationCassandra<T>(
        private val cassandra: ReactiveCassandraTemplate,
        private val userHandleRepo: ChatUserHandleRepository<T>,
        private val roomRepo: TopicRepository<T>,
        private val nameRepo: TopicByNameRepository<T>,
        private val byMemberRepo: TopicMembershipByMemberRepository<T>,
        private val byMemberOfRepo: TopicMembershipByMemberOfRepository<T>,
        private val byUserRepo: ChatMessageByUserRepository<T>,
        private val byTopicRepo: ChatMessageByTopicRepository<T>
) {
    @Bean
    fun userIndex(): UserIndexService<T> =
            UserIndexCassandra(UserCriteriaCodec(), userHandleRepo, cassandra)

    @Bean
    fun roomIndex(): TopicIndexService<T> =
            TopicIndexCassandra(TopicCriteriaCodec(), roomRepo, nameRepo)

    @Bean
    fun membershipIndex(): MembershipIndexService<T> =
            MembershipIndexCassandra(MembershipCriteriaCodec(), byMemberRepo, byMemberOfRepo)

    @Bean
    fun messageIndex(): MessageIndexService<T, String> =
            MessageIndexCassandra(MessageCriteriaCodec(), byUserRepo, byTopicRepo)
}