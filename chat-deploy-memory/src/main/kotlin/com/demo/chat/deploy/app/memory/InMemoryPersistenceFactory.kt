package com.demo.chat.deploy.app.memory

import com.demo.chat.service.*
import com.demo.chat.service.impl.memory.persistence.MembershipPersistenceInMemory
import com.demo.chat.service.impl.memory.persistence.MessagePersistenceInMemory
import com.demo.chat.service.impl.memory.persistence.TopicPersistenceInMemory
import com.demo.chat.service.impl.memory.persistence.UserPersistenceInMemory
import org.springframework.context.annotation.Bean

open class InMemoryPersistenceFactory<T, V>(private val keyService: IKeyService<T>) {
    @Bean
    open fun userPersistence(): UserPersistence<T> = UserPersistenceInMemory(keyService)

    @Bean
    open fun topicPersistence(): TopicPersistence<T> = TopicPersistenceInMemory(keyService)

    @Bean
    open fun messagePersistence(): MessagePersistence<T, V> = MessagePersistenceInMemory(keyService)

    @Bean
    open fun membershipPersistence(): MembershipPersistence<T> = MembershipPersistenceInMemory(keyService)
}