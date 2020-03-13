package com.demo.deploy.config.app

import com.demo.deploy.config.PersistenceConfigurationCassandra
import com.demo.chat.repository.cassandra.ChatMessageRepository
import com.demo.chat.repository.cassandra.ChatUserRepository
import com.demo.chat.repository.cassandra.TopicMembershipRepository
import com.demo.chat.repository.cassandra.TopicRepository
import com.demo.chat.service.IKeyService
import org.springframework.context.annotation.Configuration
import java.util.*

open class AppPersistence(msg: String) {
    @Configuration
    class PersistenceCassandra(
            keyService: IKeyService<UUID>,
            userRepo: ChatUserRepository<UUID>,
            topicRepo: TopicRepository<UUID>,
            messageRepo: ChatMessageRepository<UUID>,
            membershipRepo: TopicMembershipRepository<UUID>
    ) : PersistenceConfigurationCassandra<UUID>(keyService, userRepo, topicRepo, messageRepo, membershipRepo)
}
