package com.demo.chat.test.auth

import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.Key
import com.demo.chat.service.AuthMetadata
import com.demo.chat.service.IndexService
import com.demo.chat.service.PersistenceStore
import com.demo.chat.service.StringRoleAuthorizationMetadata
import com.demo.chat.service.impl.memory.auth.AbstractAuthorizationImpl
import com.demo.chat.test.index.MockIndexResolver
import com.demo.chat.test.persistence.MockPersistenceResolver
import com.nhaarman.mockitokotlin2.any
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mockito
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@ExtendWith(
    MockIndexResolver::class,
    MockPersistenceResolver::class
)
class AbstractAuthorizationImplTests(
    val authPersistence: PersistenceStore<Long, AuthMetadata<Long, String>>,
    val authIndex: IndexService<Long, AuthMetadata<Long, String>, IndexSearchRequest>
) : AuthorizationTests<AuthMetadata<Long, String>, Long>(
    AbstractAuthorizationImpl(
        authPersistence,
        authIndex,
        { m -> IndexSearchRequest(TestAuthIndex.PRINCIPAL, m.toString(), 1) },
        { m -> m.principal },
        { m -> m.target },
        { Key.funKey(0L) },
        { m -> m.key },
        { m -> m.principal.toString() + m.target.toString() },
        { a, _ -> a }
    ),
    { StringRoleAuthorizationMetadata(Key.funKey(1024L), Key.funKey(1L), Key.funKey(1L), "TEST") },
    { Key.funKey(1L) }
)
class TestAuthIndex {
    companion object {
        const val PRINCIPAL = "p"
        const val TARGET = "t"
        const val ID = "id"
    }
}