package com.demo.chat.service.impl.memory.persistence

import com.demo.chat.domain.Key
import com.demo.chat.domain.MessageTopic
import com.demo.chat.service.IKeyService
import java.util.function.Function

class TopicPersistenceInMemory<T>(
    keyService: IKeyService<T>,
    keyReceiver: Function<MessageTopic<T>, Key<T>>,
) : InMemoryPersistence<T, MessageTopic<T>>(keyService, MessageTopic::class.java, keyReceiver)