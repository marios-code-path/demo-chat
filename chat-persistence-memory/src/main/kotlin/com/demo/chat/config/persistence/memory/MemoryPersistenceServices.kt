package com.demo.chat.config.persistence.memory

import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.domain.Key
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.persistence.memory.impl.*
import com.demo.chat.service.core.*
import com.demo.chat.service.security.AuthMetaPersistence
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(
    prefix = "app.service.core",
    name = ["persistence"],
    havingValue = "memory",
    matchIfMissing = true
)
class MemoryPersistenceServices<T, V>(
    private val keyService: IKeyService<T>,
    private val rootKeys: RootKeys<T>,
) :
    PersistenceServiceBeans<T, V> {

    @Bean
    override fun userPersistence(): UserPersistence<T> =
        UserPersistenceInMemory(keyService) { t -> t.key }

    @Bean
    override fun topicPersistence(): TopicPersistence<T> =
        TopicPersistenceInMemory(keyService) { t -> t.key }

    @Bean
    override fun messagePersistence(): MessagePersistence<T, V> =
        MessagePersistenceInMemory(keyService) { t -> t.key }

    @Bean
    override fun membershipPersistence(): MembershipPersistence<T> =
        // A membership holds a raw id. The store reads only the id of this key.
        MembershipPersistenceInMemory(keyService) { t -> Key.of(t.key, rootKeys.of(ChatDomain.TOPIC_MEMBERSHIP).id) }

    @Bean
    override fun authMetaPersistence(): AuthMetaPersistence<T> =
        AuthMetaPersistenceInMemory(keyService) { t -> t.key }

    @Bean
    override fun keyValuePersistence(): KeyValueStore<T, Any> =
        InMemoryKeyValueStore(keyService) { t -> t.key }
}