package com.demo.chat.test

import com.demo.chat.domain.Key
import com.demo.chat.secure.AuthFilterizer
import com.demo.chat.secure.service.AbstractAuthorizationService
import com.demo.chat.secure.service.AuthPrincipleByKeySearch
import com.demo.chat.secure.service.AuthorizationMetaIndexInMemory
import com.demo.chat.secure.service.AuthorizationPersistenceInMemory
import com.demo.chat.service.AuthMetadata
import com.demo.chat.service.StringRoleAuthorizationMetadata
import com.demo.chat.test.auth.AuthorizationServiceTests
import java.util.function.Supplier
import kotlin.random.Random

class AbstractAuthorizationInMemoryServiceTests : AuthorizationServiceTests<AuthMetadata<Long, String>, Long>(
    AbstractAuthorizationService(
        AuthorizationPersistenceInMemory,
        AuthorizationMetaIndexInMemory,
        AuthPrincipleByKeySearch,
        { Key.funKey(0L) },
        { m -> m.key },
        AuthFilterizer()
    ),
    Supplier {
        StringRoleAuthorizationMetadata(
            Key.funKey(Random.nextLong()),
            Key.funKey(1L),
            Key.funKey(2L),
            "ALL",
            Long.MAX_VALUE
        )
    },
    Supplier {
        Key.funKey(1L)
    }
)