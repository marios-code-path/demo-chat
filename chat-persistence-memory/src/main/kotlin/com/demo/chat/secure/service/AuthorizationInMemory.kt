package com.demo.chat.secure.service

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.service.impl.memory.persistence.InMemoryPersistence
import com.demo.chat.service.impl.memory.persistence.KeyServiceInMemory

object IncrementingKeyService : KeyServiceInMemory<Long>({ System.nanoTime() })

object AuthorizationPersistenceInMemory :
    InMemoryPersistence<Long, AuthMetadata<Long, String>>(
        IncrementingKeyService, AuthMetadata::class.java, { t -> t.key })
