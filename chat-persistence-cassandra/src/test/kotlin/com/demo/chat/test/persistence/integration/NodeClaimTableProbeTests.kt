package com.demo.chat.test.persistence.integration

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
 * Probe for one load-bearing claim in the node id lease design.
 *
 * The claim: when owner_id expires, node_claim holds a primary key with no
 * live columns, and IF NOT EXISTS then treats the row as absent.
 *
 * This test exists before any guard code. A failure here changes the
 * schema. The fallback adds an explicit expires_at column, and that returns
 * a clock question to the design.
 *
 * Node ids 200 to 209 belong to this package. truncate-long.cql does not
 * clear node_claim, so a reused node id would meet an earlier claim.
 *
 * This class claims 210 to 212, and no other class claims them. The test
 * classes of this module share one Cassandra container, so one keyspace, since
 * `CHAT-znodyvcc`. A claim on 201 here once left a live lease that
 * `CassandraNodeIdClaimStoreTests` read as its own.
 */
@ExtendWith(SpringExtension::class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = [RepositoryTestConfiguration::class]
)
@TestPropertySource(properties = ["app.key.type=long", "app.nodeid=200"])
@Tag("integration")
class NodeClaimTableProbeTests {

    @Autowired
    lateinit var template: ReactiveCassandraTemplate

    private val cql get() = template.reactiveCqlOperations

    private fun claim(nodeId: Int, owner: String, ttlSeconds: Int): Boolean =
        cql.queryForRows(
            "INSERT INTO node_claim (node_id, owner_id) VALUES (?, ?) IF NOT EXISTS USING TTL ?",
            nodeId, owner, ttlSeconds
        ).next().map { it.getBoolean("[applied]") }.block()!!

    @Test
    fun `a second owner cannot take a live claim`() {
        Assertions.assertTrue(claim(210, "owner-one", 30))
        Assertions.assertFalse(claim(210, "owner-two", 30))
    }

    @Test
    fun `an expired claim is absent for IF NOT EXISTS`() {
        Assertions.assertTrue(claim(211, "owner-one", 1))
        Thread.sleep(Duration.ofSeconds(3).toMillis())
        Assertions.assertTrue(
            claim(211, "owner-two", 30),
            "A TTL expired row must be absent for IF NOT EXISTS. " +
                "If this fails, the design needs an explicit expires_at column."
        )
    }

    @Test
    fun `a deleted claim is absent for IF NOT EXISTS`() {
        Assertions.assertTrue(claim(212, "owner-one", 30))
        cql.execute("DELETE FROM node_claim WHERE node_id = ?", 212).block()
        Assertions.assertTrue(claim(212, "owner-two", 30))
    }
}
