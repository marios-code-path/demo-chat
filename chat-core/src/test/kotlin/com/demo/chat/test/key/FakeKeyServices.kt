package com.demo.chat.test.key

import com.demo.chat.domain.Key
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.test.TestGeneratorKeyService
import com.demo.chat.test.TestLongKeyGenerator
import java.util.UUID

/**
 * Registries over a complete set of roots, for key service tests. The domain
 * roots take 5000 and up. The identities are users. See `CHAT-avduuqwp`.
 */
object FakeKeyServices {
    fun longRoots(): RootKeys<Long> = RootKeys<Long>().apply {
        loadDomains(ChatDomain.entries.associateWith { Key.root(5000L + it.ordinal) })
        loadIdentities(Key.of(4998L, 5000L + ChatDomain.USER.ordinal), Key.of(4999L, 5000L + ChatDomain.USER.ordinal))
    }

    /** The `UUID` equivalent. The domain roots take the low bits 5000 and up. */
    fun uuidRoots(): RootKeys<UUID> = RootKeys<UUID>().apply {
        loadDomains(ChatDomain.entries.associateWith { Key.root(UUID(0x7007L, 5000L + it.ordinal)) })
        loadIdentities(Key.of(UUID(0x7007L, 4998L), of(ChatDomain.USER).id), Key.of(UUID(0x7007L, 4999L), of(ChatDomain.USER).id))
    }

    fun long(rootKeys: RootKeys<Long>): TestGeneratorKeyService<Long> =
        TestGeneratorKeyService(TestLongKeyGenerator(), rootKeys)
}

/**
 * A verifier over a registry that holds nothing. Every `resolve` and `verify`
 * fails. A test that never checks raw ids passes it to a broker.
 */
object TestVerifiers {
    fun <T> resolvingNothing(): com.demo.chat.service.core.KeyVerifier<T> =
        com.demo.chat.service.core.KeyVerifier(com.demo.chat.service.dummy.DummyKeyService(), RootKeys())
}
