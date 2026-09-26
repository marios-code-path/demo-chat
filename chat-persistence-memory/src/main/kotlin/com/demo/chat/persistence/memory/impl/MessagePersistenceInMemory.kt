package com.demo.chat.persistence.memory.impl

import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.Key
import com.demo.chat.domain.Message
import com.demo.chat.service.core.IKeyService
import com.demo.chat.service.core.MessagePersistence
import java.util.function.Function

class MessagePersistenceInMemory<T, V>(
    keyService: IKeyService<T>,
    keyFromEntity: Function<Message<T, V>, Key<T>>
) : InMemoryPersistence<T, Message<T, V>>(keyService, ChatDomain.MESSAGE, keyFromEntity),
    MessagePersistence<T, V>