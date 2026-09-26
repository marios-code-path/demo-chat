package com.demo.chat.test.persistence.integration

import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.persistence.cassandra.impl.RootKeyStoreCassandra
import com.demo.chat.service.core.IKeyGenerator
import com.demo.chat.service.core.RootKeyLoader
import com.demo.chat.test.repository.RepositoryTestConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.util.concurrent.atomic.AtomicLong

/**
 * The Cassandra root key store. See `CHAT-avduuqwp` and `CHAT-bafkgkko`.
 *
 * Each case clears `root_keys` first, so the cases stay independent. The
 * truncate scripts never clear that table. The last case proves it.
 */
@ExtendWith(SpringExtension::class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = [RepositoryTestConfiguration::class]
)
@TestPropertySource(properties = ["app.key.type=long", "app.nodeid=207"])
@Tag("integration")
class RootKeyStoreCassandraTests {

    @Autowired
    lateinit var template: ReactiveCassandraTemplate

    private val store by lazy { RootKeyStoreCassandra<Long>(template) }

    @BeforeEach
    fun `clear the root keys table`() {
        template.reactiveCqlOperations.execute("TRUNCATE root_keys").block()
    }

    @Test
    fun `an empty store gets one root per domain`() {
        val roots = RootKeyLoader(store, ids()).load().block()!!

        assertThat(roots.keys).containsExactlyInAnyOrderElementsOf(ChatDomain.entries)
        assertThat(store.read().block()).isEqualTo(roots)
    }

    @Test
    fun `a restart reads the stored roots and creates none`() {
        val first = RootKeyLoader(store, ids(0)).load().block()!!
        val second = RootKeyLoader(RootKeyStoreCassandra<Long>(template), ids(1000)).load().block()!!

        assertThat(second).isEqualTo(first)
    }

    @Test
    fun `two loaders at once agree on one root per domain`() {
        val both = Mono.zip(
            RootKeyLoader(store, ids(0)).load().subscribeOn(Schedulers.parallel()),
            RootKeyLoader(RootKeyStoreCassandra<Long>(template), ids(5000)).load().subscribeOn(Schedulers.parallel()),
        ).block()!!

        assertThat(both.t1).isEqualTo(both.t2)
    }

    @Test
    fun `the truncate script leaves the roots in place`() {
        val roots = RootKeyLoader(store, ids()).load().block()!!
        val script = javaClass.getResource("/truncate-long.cql")!!.readText()

        assertThat(script).doesNotContain("root_keys")
        template.reactiveCqlOperations.execute("TRUNCATE keys").block()
        assertThat(store.read().block()).isEqualTo(roots)
    }

    private fun ids(start: Long = 0): IKeyGenerator<Long> =
        AtomicLong(start).let { n -> object : IKeyGenerator<Long> { override fun nextId() = n.incrementAndGet() } }
}
