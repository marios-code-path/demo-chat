package com.demo.chat.service.impl.memory.persistence

import com.demo.chat.domain.Key
import com.demo.chat.domain.TopicMembership
import com.demo.chat.service.IKeyService
import java.util.function.Function

class MembershipPersistenceInMemory<T>(
    keyService: IKeyService<T>,
    keyFromEntity: Function<TopicMembership<T>, Key<T>>
) : InMemoryPersistence<T, TopicMembership<T>>(keyService, TopicMembership::class.java, keyFromEntity)