package com.demo.chat.test.persistence.integration

import com.demo.chat.domain.ClaimResult
import com.demo.chat.domain.NodeId
import com.demo.chat.persistence.cassandra.impl.CassandraNodeIdClaimStore
import com.demo.chat.test.repository.RepositoryTestConfiguration
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.time.Duration

/**
 * Store level tests for the cassandra claim.
 *
 * These call the store directly and pass the TTL as an argument. The guard
 * property rules do not apply here. Cassandra applies a TTL in whole
 * seconds, so the expiry test uses three seconds.
 *
 * Node ids 200 to 209 belong to this package. See the allocation table in
 * the plan. truncate-long.cql does not clear node_claim, so a reused node
 * id would meet an earlier claim.
 */
@ExtendWith(SpringExtension::class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = [RepositoryTestConfiguration::class]
)
@TestPropertySource(properties = ["app.key.type=long", "app.nodeid=204"])
@Tag("integration")
class CassandraNodeIdClaimStoreTests {

    @Autowired
    lateinit var template: ReactiveCassandraTemplate

    private val store by lazy { CassandraNodeIdClaimStore(template, "chat_long") }

    @Test
    fun `the scope names the backend and the keyspace`() {
        Assertions.assertEquals("cassandra", store.backendName)
        Assertions.assertEquals("cassandra keyspace chat_long", store.scope)
    }

    @Test
    fun `a second owner is denied and the holder is named`() {
        val node = NodeId(205)
        Assertions.assertEquals(
            ClaimResult.Granted,
            store.claim(node, "owner-one", Duration.ofSeconds(30)).block()
        )
        Assertions.assertEquals(
            ClaimResult.Denied("owner-one"),
            store.claim(node, "owner-two", Duration.ofSeconds(30)).block()
        )
    }

    @Test
    fun `a release allows a takeover`() {
        val node = NodeId(206)
        store.claim(node, "owner-one", Duration.ofSeconds(30)).block()
        store.release(node, "owner-one").block()

        Assertions.assertEquals(
            ClaimResult.Granted,
            store.claim(node, "owner-two", Duration.ofSeconds(30)).block()
        )
    }

    @Test
    fun `a release by another owner does nothing`() {
        val node = NodeId(207)
        store.claim(node, "owner-one", Duration.ofSeconds(30)).block()
        store.release(node, "owner-two").block()

        Assertions.assertEquals(
            ClaimResult.Denied("owner-one"),
            store.claim(node, "owner-three", Duration.ofSeconds(30)).block()
        )
    }

    @Test
    fun `an expiry allows a takeover`() {
        val node = NodeId(208)
        store.claim(node, "owner-one", Duration.ofSeconds(1)).block()
        Thread.sleep(Duration.ofSeconds(3).toMillis())

        Assertions.assertEquals(
            ClaimResult.Granted,
            store.claim(node, "owner-two", Duration.ofSeconds(30)).block()
        )
    }

    @Test
    fun `a renew by the holder is granted`() {
        val node = NodeId(209)
        store.claim(node, "owner-one", Duration.ofSeconds(30)).block()

        Assertions.assertEquals(
            ClaimResult.Granted,
            store.renew(node, "owner-one", Duration.ofSeconds(30)).block()
        )
    }

    @Test
    fun `a renew by another owner is denied and names the holder`() {
        val node = NodeId(200)
        store.claim(node, "owner-one", Duration.ofSeconds(30)).block()

        Assertions.assertEquals(
            ClaimResult.Denied("owner-one"),
            store.renew(node, "owner-two", Duration.ofSeconds(30)).block()
        )
    }

    @Test
    fun `a renew with no live claim reports lost`() {
        Assertions.assertEquals(
            ClaimResult.Lost,
            store.renew(NodeId(201), "owner-one", Duration.ofSeconds(30)).block()
        )
    }

    @Test
    fun `a missing table names the table and the schema file`() {
        val missing = CassandraNodeIdClaimStore(template, "chat_long", "node_claim_absent")

        val thrown = Assertions.assertThrows(Exception::class.java) {
            missing.claim(NodeId(202), "owner-one", Duration.ofSeconds(30)).block()
        }
        val text = generateSequence(thrown as Throwable) { it.cause }
            .mapNotNull { it.message }.joinToString(" | ")

        Assertions.assertTrue(text.contains("node_claim_absent"), text)
        Assertions.assertTrue(text.contains("keyspace-long.cql"), text)
    }
}
