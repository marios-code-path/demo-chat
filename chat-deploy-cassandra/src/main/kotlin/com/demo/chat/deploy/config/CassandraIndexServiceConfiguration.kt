package com.demo.chat.deploy.config

import com.demo.chat.repository.cassandra.*
import com.demo.chat.service.MembershipIndexService
import com.demo.chat.service.MessageIndexService
import com.demo.chat.service.TopicIndexService
import com.demo.chat.service.UserIndexService
import com.demo.chat.service.index.*
import org.springframework.context.annotation.Bean
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
        private val stringToKeyCodec: Function<String, T>
) {
    @Bean
    open fun userIndex(): UserIndexService<T, Map<String, String>> =
            UserIndexCassandra(userHandleRepo, cassandra)

    @Bean
    open fun roomIndex(): TopicIndexService<T, Map<String, String>> =
            TopicIndexCassandra(roomRepo, nameRepo)

    @Bean
    open fun membershipIndex(): MembershipIndexService<T, Map<String, String>> =
            MembershipIndexCassandra(stringToKeyCodec, byMemberRepo, byMemberOfRepo)

    @Bean
    open fun messageIndex(): MessageIndexService<T, String, Map<String, String>> =
            MessageIndexCassandra(stringToKeyCodec, byUserRepo, byTopicRepo)
}