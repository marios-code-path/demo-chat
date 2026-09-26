package com.demo.chat.test.rsocket

import com.demo.chat.domain.Key
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.service.core.KeyVerifier
import com.demo.chat.test.TestGeneratorKeyService
import com.demo.chat.test.TestUUIDKeyGenerator
import com.demo.chat.test.key.FakeKeyServices
import java.util.UUID

/**
 * The key registry of the RSocket composite tests. A test registers each id it
 * sends under its domain, so the service resolves it as production does. See
 * `CHAT-avduuqwp`.
 */
object RSocketTestRegistry {
    val roots = FakeKeyServices.uuidRoots()
    val keys = TestGeneratorKeyService(TestUUIDKeyGenerator(), roots)
    val verifier = KeyVerifier(keys, roots)

    /** This method registers a fresh id in [domain] and answers its key. */
    fun registered(domain: ChatDomain): Key<UUID> = keys.register(UUID.randomUUID(), domain)

    fun register(id: UUID, domain: ChatDomain): Key<UUID> = keys.register(id, domain)
}
