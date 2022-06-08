package com.demo.chat.service.impl.memory.persistence

import com.demo.chat.domain.Key
import com.demo.chat.domain.Message
import com.demo.chat.service.IKeyService
import com.demo.chat.service.MessagePersistence
import java.util.function.Function

class MessagePersistenceInMemory<T, V>(
    keyService: IKeyService<T>,
    keyFromEntity: Function<Message<T, V>, Key<T>>
) : InMemoryPersistence<T, Message<T, V>>(keyService, Message::class.java, keyFromEntity),
    MessagePersistence<T, V>