package com.demo.chat.persistence.memory.impl

import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.Key
import com.demo.chat.domain.TopicMembership
import com.demo.chat.service.core.IKeyService
import com.demo.chat.service.core.MembershipPersistence
import java.util.function.Function

class MembershipPersistenceInMemory<T>(
    keyService: IKeyService<T>,
    keyFromEntity: Function<TopicMembership<T>, Key<T>>
) : InMemoryPersistence<T, TopicMembership<T>>(keyService, ChatDomain.TOPIC_MEMBERSHIP, keyFromEntity),
    MembershipPersistence<T>