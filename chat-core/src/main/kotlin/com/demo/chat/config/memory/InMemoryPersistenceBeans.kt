package com.demo.chat.config.memory

import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.domain.Key
import com.demo.chat.service.IKeyService
import com.demo.chat.service.impl.memory.persistence.MembershipPersistenceInMemory
import com.demo.chat.service.impl.memory.persistence.MessagePersistenceInMemory
import com.demo.chat.service.impl.memory.persistence.TopicPersistenceInMemory
import com.demo.chat.service.impl.memory.persistence.UserPersistenceInMemory

open class InMemoryPersistenceBeans<T, V>(private val keyService: IKeyService<T>) :
    PersistenceServiceBeans<T, V> {
    override fun user() = UserPersistenceInMemory(keyService) { t -> t.key }
    override fun topic() = TopicPersistenceInMemory(keyService) { t -> t.key }
    override fun message() = MessagePersistenceInMemory<T, V>(keyService) { t -> t.key }
    override fun membership() = MembershipPersistenceInMemory(keyService) { t -> Key.funKey(t.key) }
}