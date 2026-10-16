package com.demo.chat.test.deploy.redis

import com.demo.chat.domain.ServiceLeaseException
import com.demo.chat.domain.ServiceLeaseGuard
import com.demo.chat.domain.ServiceLeaseNames
import com.demo.chat.domain.StoreWriteGate
import com.demo.chat.domain.StoreWriteGatePostProcessor
import com.demo.chat.domain.StoreWriteRefusedException
import com.demo.chat.domain.User
import com.demo.chat.service.core.UserPersistence
import com.demo.chat.test.deploy.redis.RedisLuceneFilesRestartTests.Companion.addUser
import com.demo.chat.test.deploy.redis.RedisLuceneFilesRestartTests.Companion.findsHandle
import com.demo.chat.test.deploy.redis.RedisLuceneFilesRestartTests.Companion.start
import com.demo.chat.test.deploy.redis.RedisLuceneFilesRestartTests.Companion.startWhenClaimFree
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.context.SmartLifecycle
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.core.Ordered
import org.springframework.core.PriorityOrdered
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/**
 * The store service lease on a Redis deployment. See `CHAT-lswjobhz` and
 * `CHAT-ocpojbyy`.
 *
 * Node ids 52 to 58 belong to this class. See docs/NODEID-CLAIM.md. The
 * helpers set a 3 second TTL, a 1 second renew interval and a 1 second
 * safety margin.
 */
@Tag("integration")
class RedisServiceLeaseBootTests {

    private val container = RedisDeployBootTests.redis

    private fun template(): ReactiveStringRedisTemplate = ReactiveStringRedisTemplate(
        LettuceConnectionFactory(RedisStandaloneConfiguration(container.containerIpAddress, container.getMappedPort(6379)))
            .apply { afterPropertiesSet() }
    )

    @Test
    fun `a clean close releases every lease, so a restart starts at once`() {
        startWhenClaimFree(null, 52).use { context ->
            val guard = context.getBean(ServiceLeaseGuard::class.java)
            assertThat(guard.claimed).describedAs("the claimed names").isEqualTo(ServiceLeaseNames.of(ServiceLeaseNames.servicesIn(context)))
            assertThat(guard.claimed).describedAs("every store service of a full core").isEqualTo(ServiceLeaseNames.ALL)
        }
        start(null, 52).use { }
    }

    @Test
    fun `a swap after a clean close finds the user through the rebuilt index`() {
        startWhenClaimFree(null, 53).use { a -> a.addUser("swaprelease") }
        start(null, 54).use { b -> assertThat(b.findsHandle("swaprelease")).isTrue() }
    }

    @Test
    fun `a seeded lease refuses a start until it expires`() {
        template().opsForValue().set("chat:lease:long:index:authmeta", "node 99 crashed@host:1#dead", Duration.ofSeconds(2)).block()
        val thrown = assertThrows<Exception> { start(null, 55).close() }
        assertThat(generateSequence<Throwable>(thrown) { it.cause }.any { it is ServiceLeaseException }).isTrue()

        Thread.sleep(2500)
        start(null, 55).use { }
    }

    @Test
    fun `a failed start after the claims leaves no lease`() {
        val thrown = assertThrows<Exception> {
            start(null, 56, "spring.main.sources=${FailingStart::class.java.name}").close()
        }
        assertThat(generateSequence<Throwable>(thrown) { it.cause }.any { it.message == FailingStart.MESSAGE }).isTrue()

        start(null, 56).use { }
    }

    @Test
    fun `a lost lease closes the context`() {
        val context = startWhenClaimFree(null, 57)
        template().delete("chat:lease:long:index:user").block()

        val deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos()
        while (context.isActive && System.nanoTime() < deadline) Thread.sleep(100)
        assertThat(context.isActive).describedAs("the context after its lease was deleted").isFalse()
    }

    @Test
    fun `a write that is active during the close ends before the release`() {
        val context = startWhenClaimFree(null, 58, "spring.main.sources=${SlowUserWrites::class.java.name}")
        val leases = template()
        val releasedAt = AtomicLong(0)
        val watch = thread {
            val end = System.nanoTime() + Duration.ofSeconds(20).toNanos()
            while (releasedAt.get() == 0L && System.nanoTime() < end) {
                if (leases.hasKey("chat:lease:long:persistence:user").block() == false) releasedAt.set(System.nanoTime())
                else Thread.sleep(10)
            }
        }
        @Suppress("UNCHECKED_CAST")
        val users = context.getBean("userPersistence") as UserPersistence<Long>
        val key = users.key().block(Duration.ofSeconds(5))!!
        val write = users.add(User.create(key, "Drain", "drainwrite", "http://u")).toFuture()
        assertThat(SlowUserWrites.entered.await(5, TimeUnit.SECONDS)).isTrue()
        assertThat(StoreWriteGatePostProcessor.isGated(users)).describedAs("the user store bean is gated").isTrue()
        assertThat(context.getBean(StoreWriteGate::class.java).active).describedAs("the active writes before the close").isPositive()

        context.close()
        runCatching { write.get(10, TimeUnit.SECONDS) }
        watch.join(20_000)

        assertThat(releasedAt.get()).describedAs("the release time").isNotZero()
        assertThat(SlowUserWrites.finishedAt.get()).describedAs("the write end").isNotZero().isLessThan(releasedAt.get())
        val refused = runCatching { users.add(User.create(key, "Late", "latewrite", "http://u")).block(Duration.ofSeconds(5)) }
        assertThat(refused.exceptionOrNull()).isInstanceOf(StoreWriteRefusedException::class.java)
    }

    /** Delays each user write by 2 s. The delay runs inside the write gate. */
    @Configuration
    open class SlowUserWrites {
        companion object {
            val entered = CountDownLatch(1)
            val finishedAt = AtomicLong(0)

            // Spring reads PriorityOrdered from the declared bean type. So the
            // method declares the class, and Spring applies it before the gate
            // post processor. The gate proxy then wraps this delay.
            @Bean
            @JvmStatic
            fun slowUserWrites(): SlowUserWritesProcessor = SlowUserWritesProcessor()
        }
    }

    class SlowUserWritesProcessor : BeanPostProcessor, PriorityOrdered {
        override fun getOrder() = Ordered.HIGHEST_PRECEDENCE

        @Suppress("UNCHECKED_CAST")
        override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
            if (beanName != "userPersistence") return bean
            val target = bean as UserPersistence<Long>
            return object : UserPersistence<Long> by target {
                override fun add(ent: User<Long>): Mono<Void> = Mono.defer {
                    SlowUserWrites.entered.countDown()
                    Mono.delay(Duration.ofSeconds(2)).then(target.add(ent))
                        .doFinally { SlowUserWrites.finishedAt.set(System.nanoTime()) }
                }
            }
        }
    }

    /** A start step after the guards. Its failure fails the refresh. */
    @Configuration
    open class FailingStart {
        @Bean
        open fun failingStart(): SmartLifecycle = object : SmartLifecycle {
            override fun start() = throw IllegalStateException(MESSAGE)
            override fun stop() {}
            override fun isRunning() = false
            override fun getPhase() = Int.MAX_VALUE - 4500
        }

        companion object {
            const val MESSAGE = "A test start step failed after the lease guards claimed."
        }
    }
}
