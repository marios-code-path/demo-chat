package com.demo.chat.test.persistence.redis

import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyRootConflictException
import com.demo.chat.domain.RootKeyRegistrationException
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.persistence.redis.impl.KeyServiceRedis
import com.demo.chat.test.key.TestKeyServiceBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import reactor.test.StepVerifier
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.util.UUID
import com.demo.chat.domain.knownkey.RootKeys

@Extensions(
    ExtendWith(SpringExtension::class)
)
@Import(RedisPersistenceTestContext::class, RedisPersistenceTestBeans::class)
@Tag("integration")
class RedisKeyServiceTests(
    @Autowired private val keys: KeyServiceRedis<UUID>,
    @Autowired private val roots: RootKeys<UUID>,
    @Autowired private val stringTemplate: ReactiveStringRedisTemplate,
) : TestKeyServiceBase<UUID>(keys, roots) {

    @BeforeEach
    fun `flush redis`() {
        stringTemplate.delete(stringTemplate.keys("*")).block()
    }

    @Test
    fun `a new key registers under its root`() {
        val id = UUID.randomUUID()
        val root = roots.of(ChatDomain.MESSAGE).id
        StepVerifier.create(keys.register(Key.of(id, root))).verifyComplete()
        assertThat(keys.rootOf(id).block()).isEqualTo(root)
    }

    @Test
    fun `a repeated registration succeeds and keeps one root`() {
        val id = UUID.randomUUID()
        val root = roots.of(ChatDomain.MESSAGE).id
        keys.register(Key.of(id, root)).block()
        StepVerifier.create(keys.register(Key.of(id, root))).verifyComplete()
        assertThat(keys.rootOf(id).block()).isEqualTo(root)
    }

    @Test
    fun `a different root fails with a conflict and keeps the stored root`() {
        val id = UUID.randomUUID()
        val root = roots.of(ChatDomain.MESSAGE).id
        keys.register(Key.of(id, root)).block()
        StepVerifier.create(keys.register(Key.of(id, roots.of(ChatDomain.USER).id)))
            .expectError(KeyRootConflictException::class.java)
            .verify()
        assertThat(keys.rootOf(id).block()).isEqualTo(root)
    }

    @Test
    fun `a root key id is refused`() {
        val root = roots.of(ChatDomain.MESSAGE).id
        StepVerifier.create(keys.register(Key.of(root, root)))
            .expectError(RootKeyRegistrationException::class.java)
            .verify()
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun containerSetup(registry: DynamicPropertyRegistry) = RedisTestContainer.properties(registry)
    }
}