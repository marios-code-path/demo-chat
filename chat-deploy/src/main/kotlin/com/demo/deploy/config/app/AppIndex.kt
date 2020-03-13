package com.demo.deploy.config.app

import com.demo.deploy.config.IndexConfigurationCassandra
import com.demo.chat.repository.cassandra.*
import org.springframework.context.annotation.Configuration
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import java.util.*

open class AppIndex(msg: String) {

    @Configuration
    class IndexCassandra(
            cassandra: ReactiveCassandraTemplate,
            userHandleRepo: ChatUserHandleRepository<UUID>,
            roomRepo: TopicRepository<UUID>,
            nameRepo: TopicByNameRepository<UUID>,
            byMemberRepo: TopicMembershipByMemberRepository<UUID>,
            byMemberOfRepo: TopicMembershipByMemberOfRepository<UUID>,
            byUserRepo: ChatMessageByUserRepository<UUID>,
            byTopicRepo: ChatMessageByTopicRepository<UUID>
    ) : IndexConfigurationCassandra<UUID>(cassandra, userHandleRepo, roomRepo, nameRepo, byMemberRepo, byMemberOfRepo, byUserRepo, byTopicRepo)
}