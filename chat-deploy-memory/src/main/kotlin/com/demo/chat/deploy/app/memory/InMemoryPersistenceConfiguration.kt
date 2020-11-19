package com.demo.chat.deploy.app.memory

import com.demo.chat.service.*
import com.demo.chat.service.impl.memory.persistence.MembershipPersistenceInMemory
import com.demo.chat.service.impl.memory.persistence.MessagePersistenceInMemory
import com.demo.chat.service.impl.memory.persistence.TopicPersistenceInMemory
import com.demo.chat.service.impl.memory.persistence.UserPersistenceInMemory
import org.springframework.context.annotation.Bean

interface PersistenceResourceFactory<T, V> {
    fun user(): UserPersistence<T>
    fun topic(): TopicPersistence<T>
    fun message(): MessagePersistence<T, V>
    fun membership(): MembershipPersistence<T>
}

open class InMemoryPersistenceConfiguration<T, V>(private val keyService: IKeyService<T>) : PersistenceResourceFactory<T, V> {
    @Bean
    override fun user(): UserPersistence<T> = UserPersistenceInMemory(keyService)

    @Bean
    override fun topic(): TopicPersistence<T> = TopicPersistenceInMemory(keyService)

    @Bean
    override fun message(): MessagePersistence<T, V> = MessagePersistenceInMemory(keyService)

    @Bean
    override fun membership(): MembershipPersistence<T> = MembershipPersistenceInMemory(keyService)
}