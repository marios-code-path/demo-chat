package com.demo.chat.secure.service
import com.demo.chat.domain.Key
import com.demo.chat.service.AuthMetadata
import com.demo.chat.service.IKeyService
import com.demo.chat.service.impl.memory.persistence.InMemoryPersistence
import java.util.function.Function

open class AuthMetaPersistenceInMemory<T, P>(
    keyService: IKeyService<T>,
    keyReceiver: Function<AuthMetadata<T, P>, Key<T>>
) : InMemoryPersistence<T, AuthMetadata<T, P>>(keyService, AuthMetadata::class.java, keyReceiver)