package com.demo.chat.persistence.memory.impl

import com.demo.chat.domain.knownkey.RootKeys

import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.Key
import com.demo.chat.domain.AuthMetadata
import com.demo.chat.service.core.IKeyService
import com.demo.chat.service.security.AuthMetaPersistence
import java.util.function.Function

open class AuthMetaPersistenceInMemory<T>(
    keyService: IKeyService<T>,
    rootKeys: RootKeys<T>,
    keyReceiver: Function<AuthMetadata<T>, Key<T>>
) : InMemoryPersistence<T, AuthMetadata<T>>(keyService, ChatDomain.AUTH_METADATA, rootKeys, keyReceiver),
    AuthMetaPersistence<T>
