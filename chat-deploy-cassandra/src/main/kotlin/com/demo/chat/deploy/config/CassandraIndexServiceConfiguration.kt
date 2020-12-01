package com.demo.chat.deploy.config

import com.demo.chat.deploy.config.core.IndexServiceFactory
import com.demo.chat.repository.cassandra.*
import com.demo.chat.service.index.MembershipIndexCassandra
import com.demo.chat.service.index.MessageIndexCassandra
import com.demo.chat.service.index.TopicIndexCassandra
import com.demo.chat.service.index.UserIndexCassandra
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
        private val stringToKeyCodec: Function<String, T>,
) : IndexServiceFactory<T, String, Map<String, String>> {
    @Bean
    override fun userIndex() =
            UserIndexCassandra(userHandleRepo, cassandra)

    @Bean
    override fun topicIndex() =
            TopicIndexCassandra(roomRepo, nameRepo)

    @Bean
    override fun membershipIndex() =
            MembershipIndexCassandra(stringToKeyCodec, byMemberRepo, byMemberOfRepo)

    @Bean
    override fun messageIndex() =
            MessageIndexCassandra(stringToKeyCodec, byUserRepo, byTopicRepo)
}