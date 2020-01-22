package com.demo.chat.config

import com.demo.chat.ExcludeFromTests
import com.demo.chat.repository.cassandra.*
import com.demo.chat.service.MembershipIndexService
import com.demo.chat.service.MessageIndexService
import com.demo.chat.service.TopicIndexService
import com.demo.chat.service.UserIndexService
import com.demo.chat.service.index.*
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import java.util.*


@ExcludeFromTests
@Profile("cassandra-index")
@Configuration
class IndexConfiguration {
    @Bean
    fun userIndex(userHandleRepo: ChatUserHandleRepository<UUID>,
                  cassandra: ReactiveCassandraTemplate): UserIndexService<UUID> =
            UserIndexCassandra(UserCriteriaCodec(), userHandleRepo, cassandra)

    @Bean
    fun roomIndex(roomRepo: TopicRepository<UUID>,
                  nameRepo: TopicByNameRepository<UUID>): TopicIndexService<UUID> =
            TopicIndexCassandra(TopicCriteriaCodec(), roomRepo, nameRepo)

    @Bean
    fun membershipIndex(byMemberRepo: TopicMembershipByMemberRepository<UUID>,
                        byMemberOfRepo: TopicMembershipByMemberOfRepository<UUID>): MembershipIndexService<UUID> =
            MembershipIndexCassandra(MembershipCriteriaCodec(), byMemberRepo, byMemberOfRepo)

    @Bean
    fun messageIndex(cassandra: ReactiveCassandraTemplate,
                     byUserRepo: ChatMessageByUserRepository<UUID>,
                     byTopicRepo: ChatMessageByTopicRepository<UUID>): MessageIndexService<UUID> =
            MessageIndexCassandra(MessageCriteriaCodec(), byUserRepo, byTopicRepo)
}