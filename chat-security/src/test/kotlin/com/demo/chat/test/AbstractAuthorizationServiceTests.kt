package com.demo.chat.test

import com.demo.chat.domain.*
import com.demo.chat.secure.AuthMetadataPrincipleKeySearch
import com.demo.chat.secure.AuthMetadataTargetKeySearch
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
    authMetaPersistence: PersistenceStore<Long, AuthMetadata<Long>>,
    authMetaIndex: IndexService<Long, AuthMetadata<Long>, IndexSearchRequest>
) : AuthorizationServiceTests<Long>(
    AbstractAuthorizationService(
        authMetaPersistence,
        authMetaIndex,
        AuthMetadataPrincipleKeySearch(LongUtil()),
        AuthMetadataTargetKeySearch(LongUtil()),
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