package com.demo.chat.persistence.memory.impl

import com.demo.chat.domain.Key
import com.demo.chat.domain.MessageTopic
import com.demo.chat.service.core.IKeyService
import com.demo.chat.service.core.TopicPersistence
import java.util.function.Function

class TopicPersistenceInMemory<T>(
    keyService: IKeyService<T>,
    keyFromEntity: Function<MessageTopic<T>, Key<T>>,
) : InMemoryPersistence<T, MessageTopic<T>>(keyService, MessageTopic::class.java, keyFromEntity),
    TopicPersistence<T>