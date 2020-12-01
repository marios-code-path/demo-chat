package com.demo.chat.service.impl.memory.persistence

import com.demo.chat.domain.Key
import com.demo.chat.domain.TopicMembership
import com.demo.chat.domain.User
import com.demo.chat.service.IKeyService
import com.demo.chat.service.MembershipPersistence
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function

class MembershipPersistenceInMemory<T>(
        keyService: IKeyService<T>,
        classId: Class<*>,
        keyReceiver: Function<TopicMembership<T>, Key<T>>
)
    : InMemoryPersistence<T, TopicMembership<T>>(keyService, classId, keyReceiver)