package com.demo.chat.test.persistence.memory

import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyRootConflictException
import com.demo.chat.domain.RootKeyRegistrationException
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.persistence.memory.impl.KeyServiceInMemory
import com.demo.chat.test.key.FakeKeyServices
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import reactor.test.StepVerifier
import java.util.concurrent.atomic.AtomicLong

class KeyServiceInMemoryRegistrationTests {
    private val roots = FakeKeyServices.longRoots()
    private val ids = AtomicLong(1000)
    private val keys = KeyServiceInMemory({ ids.incrementAndGet() }, roots)
    private val messageRoot = roots.of(ChatDomain.MESSAGE).id
    private val userRoot = roots.of(ChatDomain.USER).id

    @Test
    fun `a new key registers under its root`() {
        StepVerifier.create(keys.register(Key.of(42L, messageRoot))).verifyComplete()
        assertThat(keys.rootOf(42L).block()).isEqualTo(messageRoot)
    }

    @Test
    fun `a repeated registration succeeds and keeps one root`() {
        keys.register(Key.of(43L, messageRoot)).block()
        StepVerifier.create(keys.register(Key.of(43L, messageRoot))).verifyComplete()
        assertThat(keys.rootOf(43L).block()).isEqualTo(messageRoot)
    }

    @Test
    fun `a different root fails with a conflict and keeps the stored root`() {
        keys.register(Key.of(44L, messageRoot)).block()
        StepVerifier.create(keys.register(Key.of(44L, userRoot)))
            .expectError(KeyRootConflictException::class.java)
            .verify()
        assertThat(keys.rootOf(44L).block()).isEqualTo(messageRoot)
    }

    @Test
    fun `a root key id is refused`() {
        StepVerifier.create(keys.register(Key.of(messageRoot, messageRoot)))
            .expectError(RootKeyRegistrationException::class.java)
            .verify()
    }
}
