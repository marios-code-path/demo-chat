package com.demo.chat.service.security

import com.demo.chat.domain.Key
import com.demo.chat.domain.AuthMetadata
import com.demo.chat.service.IKeyService
import com.demo.chat.service.impl.memory.persistence.InMemoryPersistence
import java.util.function.Function

open class AuthMetaPersistenceInMemory<T>(
    keyService: IKeyService<T>,
    keyReceiver: Function<AuthMetadata<T>, Key<T>>
) : InMemoryPersistence<T, AuthMetadata<T>>(keyService, AuthMetadata::class.java, keyReceiver),
    AuthMetaPersistence<T>
