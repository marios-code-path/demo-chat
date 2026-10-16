package com.demo.chat.test.domain

import com.demo.chat.domain.*
import com.demo.chat.service.core.UserIndexService
import com.demo.chat.service.core.UserPersistence
import com.demo.chat.service.core.TopicIndexService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.context.support.GenericApplicationContext
import reactor.core.publisher.Mono
import java.lang.reflect.Proxy
import java.time.Duration

/** The store service lease guard. See `CHAT-lswjobhz`. */
class ServiceLeaseGuardTests {

    /** A store whose answers the test sets, per lease name. */
    class FakeLeaseStore : ServiceLeaseStore {
        override val backendName = "redis"
        override val scope = "redis store for key type long"
        val calls = java.util.Collections.synchronizedList(mutableListOf<String>())
        val denied = mutableMapOf<String, String>()
        var renewAnswer: () -> ClaimResult = { ClaimResult.Granted }

        override fun claim(name: String, owner: String, ttl: Duration): Mono<ClaimResult> = Mono.fromCallable {
            calls.add("claim:$name")
            denied[name]?.let { ClaimResult.Denied(it) } ?: ClaimResult.Granted
        }

        override fun renew(name: String, owner: String, ttl: Duration): Mono<ClaimResult> =
            Mono.fromCallable { calls.add("renew:$name"); renewAnswer() }

        override fun release(name: String, owner: String): Mono<Void> =
            Mono.fromRunnable { calls.add("release:$name") }
    }

    private val store = FakeLeaseStore()
    private val scheduler = ManualClaimScheduler()
    private val context = GenericApplicationContext().apply { refresh() }
    private val owner = RuntimeOwnerId.generate("test")

    private fun stub(type: Class<*>): Any = Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, _, _ -> null }

    private val userServices = mapOf(
        "userPersistence" to stub(UserPersistence::class.java),
        "userIndex" to stub(UserIndexService::class.java),
    )

    private fun guard(services: Map<String, Any> = userServices) = ServiceLeaseGuard(
        store, { services }, NodeId(7), owner, NodeIdClaimProperties(null, null, null, null), context, scheduler,
        clock = { 0L },
    )

    @Test
    fun `the guard claims the name of every present service in sorted order`() {
        val guard = guard()
        guard.start()
        assertThat(store.calls).containsExactly("claim:index:user", "claim:persistence:user")
        assertThat(guard.claimed).containsExactly("index:user", "persistence:user")
    }

    @Test
    fun `a store service with no lease name fails the start before any claim`() {
        val guard = guard(userServices + ("orphanStore" to stub(com.demo.chat.service.core.PersistenceStore::class.java)))
        assertThatThrownBy { guard.start() }.hasMessageContaining("orphanStore")
        assertThat(store.calls).isEmpty()
        assertThat(guard.isRunning).isFalse()
    }

    @Test
    fun `a refused claim releases the leases already taken and names the service, scope and holder`() {
        store.denied["index:user"] = "node 9 other@host:1#abc"
        val guard = guard(userServices + ("topicIndex" to stub(TopicIndexService::class.java)))

        assertThatThrownBy { guard.start() }
            .isInstanceOf(ServiceLeaseException::class.java)
            .hasMessageContaining("index:user")
            .hasMessageContaining("redis store for key type long")
            .hasMessageContaining("node 9 other@host:1#abc")
        assertThat(store.calls).containsExactly("claim:index:topic", "claim:index:user", "release:index:topic")
        assertThat(guard.isRunning).isFalse()
    }

    @Test
    fun `the refusal message asks for confirmed termination`() {
        store.denied["index:user"] = "node 9 other@host:1#abc"
        assertThatThrownBy { guard().start() }
            .hasMessageContaining("confirm that its process exited")
            .hasMessageNotContaining("one live instance")
    }

    @Test
    fun `the holder names the node id and the runtime owner`() {
        val owners = mutableListOf<String>()
        val recording = object : ServiceLeaseStore by store {
            override fun claim(name: String, owner: String, ttl: Duration): Mono<ClaimResult> {
                owners.add(owner)
                return store.claim(name, owner, ttl)
            }
        }
        ServiceLeaseGuard(recording, { mapOf("userIndex" to stub(UserIndexService::class.java)) }, NodeId(7), owner,
            NodeIdClaimProperties(null, null, null, null), context, scheduler, clock = { 0L }).start()
        assertThat(owners.single()).isEqualTo("node 7 ${owner.value}")
    }

    @Test
    fun `stop releases in reverse order, and destroy after stop releases nothing`() {
        val guard = guard()
        guard.start()
        guard.stop()
        guard.destroy()
        assertThat(store.calls.filter { it.startsWith("release") })
            .containsExactly("release:persistence:user", "release:index:user")
    }

    @Test
    fun `destroy releases when stop did not run`() {
        val guard = guard(mapOf("userIndex" to stub(UserIndexService::class.java)))
        guard.start()
        guard.destroy()
        assertThat(store.calls).containsExactly("claim:index:user", "release:index:user")
    }

    @Test
    fun `a lost lease closes the context`() {
        guard(mapOf("userIndex" to stub(UserIndexService::class.java))).start()
        store.renewAnswer = { ClaimResult.Lost }
        scheduler.firePeriodic()
        assertThat(scheduler.detached).containsExactly("service-lease-close")
        assertThat(context.isActive).isFalse()
    }

    @Test
    fun `the guard arms the deadline at 25 s`() {
        guard(mapOf("userIndex" to stub(UserIndexService::class.java))).start()
        assertThat(scheduler.onceDelay).isEqualTo(Duration.ofSeconds(25))
        // The clock stays at 0, so a watch that fires early only rearms.
        scheduler.fireOnce()
        assertThat(context.isActive).isTrue()
    }

    @Test
    fun `no store services means no claim and a running guard`() {
        val guard = guard(emptyMap())
        guard.start()
        assertThat(store.calls).isEmpty()
        assertThat(guard.isRunning).isTrue()
        assertThat(guard.phase).isEqualTo(LeasePhases.SERVICE_GUARD)
    }
}
