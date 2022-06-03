package com.demo.chat.test

import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.StringRoleAuthorizationMetadata
import com.demo.chat.secure.AuthMetaPrincipleByKeySearch
import com.demo.chat.secure.AuthMetaTargetByKeySearch
import com.demo.chat.secure.AuthSummarizer
import com.demo.chat.secure.service.AbstractAuthorizationService
import com.demo.chat.service.IndexService
import com.demo.chat.service.PersistenceStore
import com.demo.chat.test.auth.AuthorizationServiceTests
import com.demo.chat.test.index.MockIndexResolver
import com.demo.chat.test.persistence.MockPersistenceResolver
import org.junit.jupiter.api.extension.ExtendWith
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Supplier
import kotlin.random.Random

@ExtendWith(MockPersistenceResolver::class, MockIndexResolver::class)
class AbstractAuthorizationServiceTests(
    authMetaPersistence: PersistenceStore<Long, AuthMetadata<Long, String>>,
    authMetaIndex: IndexService<Long, AuthMetadata<Long, String>, IndexSearchRequest>
) : AuthorizationServiceTests<Long, String>(
    AbstractAuthorizationService(
        authMetaPersistence,
        authMetaIndex,
        AuthMetaPrincipleByKeySearch,
        AuthMetaTargetByKeySearch,
        { Key.funKey(ANON_ID) },
        { m -> m.key },
        AuthSummarizer { a, b -> (a.key.id - b.key.id).toInt() }
    ),
    Supplier {
        StringRoleAuthorizationMetadata(
            Key.funKey(atomicLong.incrementAndGet()),
            Key.funKey(PRINCIPAL_ID),
            Key.funKey(TARGET_ID),
            TEST_PERMISSION,
            Long.MAX_VALUE
        )
    }
) {
    companion object {
        val atomicLong = AtomicLong(Random.nextLong(1024, 2048))
        const val ANON_ID = 0L
        const val TARGET_ID = 2L
        const val PRINCIPAL_ID = 1L
        const val TEST_PERMISSION = "ALL"
    }
}