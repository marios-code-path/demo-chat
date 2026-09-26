package com.demo.chat.test.key

import com.demo.chat.domain.Key
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.test.TestGeneratorKeyService
import com.demo.chat.test.TestLongKeyGenerator

/**
 * A `Long` registry over a complete set of roots, for verifier tests. The
 * domain roots take 5000 and up. The identities are users. See `CHAT-avduuqwp`.
 */
object FakeKeyServices {
    fun longRoots(): RootKeys<Long> = RootKeys<Long>().apply {
        loadDomains(ChatDomain.entries.associateWith { Key.root(5000L + it.ordinal) })
        loadIdentities(Key.of(4998L, 5000L + ChatDomain.USER.ordinal), Key.of(4999L, 5000L + ChatDomain.USER.ordinal))
    }

    fun long(rootKeys: RootKeys<Long>): TestGeneratorKeyService<Long> =
        TestGeneratorKeyService(TestLongKeyGenerator(), rootKeys)
}
