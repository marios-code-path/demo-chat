package com.demo.chat.config.memory

import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.domain.Key
import com.demo.chat.service.*
import com.demo.chat.service.impl.memory.persistence.MembershipPersistenceInMemory
import com.demo.chat.service.impl.memory.persistence.MessagePersistenceInMemory
import com.demo.chat.service.impl.memory.persistence.TopicPersistenceInMemory
import com.demo.chat.service.impl.memory.persistence.UserPersistenceInMemory
import org.springframework.context.annotation.Bean

open class InMemoryPersistenceBeans<T, V>(private val keyService: IKeyService<T>) :
    PersistenceServiceBeans<T, V> {

    @Bean
    override fun user(): UserPersistence<T> =
        UserPersistenceInMemory(keyService) { t -> t.key }

    @Bean
    override fun topic(): TopicPersistence<T> = // PersistenceStore<T, MessageTopic<T>> =
        TopicPersistenceInMemory(keyService) { t -> t.key }

    @Bean
    override fun message(): MessagePersistence<T, V> = //PersistenceStore<T, Message<T, V>> =
        MessagePersistenceInMemory(keyService) { t -> t.key }

    @Bean
    override fun membership(): MembershipPersistence<T> = //PersistenceStore<T, TopicMembership<T>> =
        MembershipPersistenceInMemory(keyService) { t -> Key.funKey(t.key) }
}