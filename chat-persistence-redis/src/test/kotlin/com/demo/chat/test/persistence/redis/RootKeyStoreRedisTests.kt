package com.demo.chat.test.persistence.redis

import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.UUIDUtil
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.persistence.redis.impl.RootKeyStoreRedis
import com.demo.chat.service.core.IKeyGenerator
import com.demo.chat.service.core.RootKeyLoader
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/** The Redis root key store. See `CHAT-avduuqwp` and `CHAT-bafkgkko`. */
@Extensions(ExtendWith(SpringExtension::class))
@Import(RedisPersistenceTestContext::class)
@Tag("integration")
class RootKeyStoreRedisTests(@Autowired private val template: ReactiveStringRedisTemplate) {

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun containerSetup(registry: DynamicPropertyRegistry) = RedisTestContainer.properties(registry)
    }

    @BeforeEach
    fun `remove the root key hashes`() {
        template.delete(template.keys("chat:rootkeys:*")).block()
    }

    @Test
    fun `an empty store gets one root per domain`() {
        val roots = RootKeyLoader(longStore(), longIds()).load().block()!!

        assertThat(roots.keys).containsExactlyInAnyOrderElementsOf(ChatDomain.entries)
        assertThat(template.opsForHash<String, String>().size("chat:rootkeys:long").block()).isEqualTo(ChatDomain.entries.size.toLong())
    }

    @Test
    fun `a restart reads the stored roots and creates none`() {
        val first = RootKeyLoader(longStore(), longIds(0)).load().block()!!
        val second = RootKeyLoader(longStore(), longIds(1000)).load().block()!!

        assertThat(second).isEqualTo(first)
    }

    @Test
    fun `two loaders at once agree on one root per domain`() {
        val both = Mono.zip(
            RootKeyLoader(longStore(), longIds(0)).load().subscribeOn(Schedulers.parallel()),
            RootKeyLoader(longStore(), longIds(5000)).load().subscribeOn(Schedulers.parallel()),
        ).block()!!

        assertThat(both.t1).isEqualTo(both.t2)
    }

    @Test
    fun `two key types on one redis keep separate roots`() {
        RootKeyLoader(longStore(), longIds()).load().block()
        val uuidRoots = RootKeyLoader(RootKeyStoreRedis(template, UUIDUtil(), "uuid"), uuidIds()).load().block()!!

        assertThat(template.hasKey("chat:rootkeys:long").block()).isTrue()
        assertThat(template.hasKey("chat:rootkeys:uuid").block()).isTrue()
        assertThat(uuidRoots.keys).containsExactlyInAnyOrderElementsOf(ChatDomain.entries)
    }

    private fun longStore() = RootKeyStoreRedis(template, TypeUtil.LongUtil, "long")

    private fun longIds(start: Long = 0): IKeyGenerator<Long> =
        AtomicLong(start).let { n -> object : IKeyGenerator<Long> { override fun nextId() = n.incrementAndGet() } }

    private fun uuidIds(): IKeyGenerator<UUID> = object : IKeyGenerator<UUID> { override fun nextId(): UUID = UUID.randomUUID() }
}
