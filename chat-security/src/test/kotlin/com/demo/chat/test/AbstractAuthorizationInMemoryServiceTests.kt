package com.demo.chat.test

import com.demo.chat.domain.Key
import com.demo.chat.domain.StringRoleAuthorizationMetadata
import com.demo.chat.secure.AuthPrincipleByKeySearch
import com.demo.chat.secure.AuthSummarizer
import com.demo.chat.secure.AuthTargetByKeySearch
import com.demo.chat.secure.service.*
import com.demo.chat.test.auth.AuthorizationServiceTests
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Supplier
import kotlin.random.Random

class AbstractAuthorizationInMemoryServiceTests : AuthorizationServiceTests<Long, String>(
    AbstractAuthorizationService(
        MockAuthMetaPersistence,
        MockAuthMetaIndex,
        AuthPrincipleByKeySearch,
        AuthTargetByKeySearch,
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