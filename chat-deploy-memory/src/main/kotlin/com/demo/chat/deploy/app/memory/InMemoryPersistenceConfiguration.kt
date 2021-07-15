package com.demo.chat.deploy.app.memory

import com.demo.chat.deploy.config.core.PersistenceServiceConfiguration
import com.demo.chat.domain.*
import com.demo.chat.service.*
import com.demo.chat.service.impl.memory.persistence.MembershipPersistenceInMemory
import com.demo.chat.service.impl.memory.persistence.MessagePersistenceInMemory
import com.demo.chat.service.impl.memory.persistence.TopicPersistenceInMemory
import com.demo.chat.service.impl.memory.persistence.UserPersistenceInMemory

open class InMemoryPersistenceConfiguration<T, V>(private val keyService: IKeyService<T>) : PersistenceServiceConfiguration<T, V> {
    override fun user() = UserPersistenceInMemory(keyService) { t -> t.key }
    override fun topic() = TopicPersistenceInMemory(keyService) { t -> t.key }
    override fun message() = MessagePersistenceInMemory<T, V>(keyService) { t -> t.key }
    override fun membership() = MembershipPersistenceInMemory(keyService) { t -> Key.funKey(t.key) }
}