package com.demo.chat.deploy.app.memory

import com.demo.chat.deploy.config.factory.PersistenceServiceFactory
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.TopicMembership
import com.demo.chat.domain.User
import com.demo.chat.service.*
import com.demo.chat.service.impl.memory.persistence.MembershipPersistenceInMemory
import com.demo.chat.service.impl.memory.persistence.MessagePersistenceInMemory
import com.demo.chat.service.impl.memory.persistence.TopicPersistenceInMemory
import com.demo.chat.service.impl.memory.persistence.UserPersistenceInMemory
import org.springframework.context.annotation.Bean

open class InMemoryPersistenceConfiguration<T, V>(private val keyService: IKeyService<T>) : PersistenceServiceFactory<T, V> {
    @Bean
    override fun user() = UserPersistenceInMemory(keyService)

    @Bean
    override fun topic() = TopicPersistenceInMemory(keyService)

    @Bean
    override fun message() = MessagePersistenceInMemory<T, V>(keyService)

    @Bean
    override fun membership() = MembershipPersistenceInMemory(keyService)
}