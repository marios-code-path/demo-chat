package com.demo.chat.test.persistence.integration

import com.demo.chat.domain.ClaimResult
import com.demo.chat.persistence.cassandra.impl.CassandraServiceLeaseStore
import com.demo.chat.test.repository.RepositoryTestConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.test.StepVerifier
import java.time.Duration

/**
 * Store level tests for the Cassandra store service lease. See
 * `CHAT-lswjobhz`.
 *
 * These call the store directly and pass the TTL as an argument. Each test
 * uses its own lease names, because truncate-long.cql does not clear
 * service_lease. The context takes node id 203 of this package.
 */
@ExtendWith(SpringExtension::class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = [RepositoryTestConfiguration::class]
)
@TestPropertySource(properties = ["app.key.type=long", "app.nodeid=203"])
@Tag("integration")
class CassandraServiceLeaseStoreTests {

    @Autowired
    lateinit var template: ReactiveCassandraTemplate

    private fun store(table: String = "service_lease") = CassandraServiceLeaseStore(template, "chat_long", table)
    private val long = Duration.ofSeconds(30)

    @Test
    fun `a claim is granted, and a second owner is denied with the holder named`() {
        StepVerifier.create(store().claim("index:contract-a", "owner-1", long)).expectNext(ClaimResult.Granted).verifyComplete()
        StepVerifier.create(store().claim("index:contract-a", "owner-2", long))
            .expectNext(ClaimResult.Denied("owner-1")).verifyComplete()
    }

    @Test
    fun `the owner renews, and another owner is denied`() {
        store().claim("index:contract-b", "owner-1", long).block()
        StepVerifier.create(store().renew("index:contract-b", "owner-1", long)).expectNext(ClaimResult.Granted).verifyComplete()
        StepVerifier.create(store().renew("index:contract-b", "owner-2", long))
            .expectNext(ClaimResult.Denied("owner-1")).verifyComplete()
    }

    @Test
    fun `a release by another owner keeps the lease`() {
        store().claim("index:contract-c", "owner-1", long).block()
        store().release("index:contract-c", "owner-2").block()
        StepVerifier.create(store().claim("index:contract-c", "owner-3", long))
            .expectNext(ClaimResult.Denied("owner-1")).verifyComplete()
        store().release("index:contract-c", "owner-1").block()
        StepVerifier.create(store().claim("index:contract-c", "owner-3", long)).expectNext(ClaimResult.Granted).verifyComplete()
    }

    @Test
    fun `an expired lease reports lost to its owner and admits a new owner`() {
        store().claim("index:contract-d", "owner-1", Duration.ofSeconds(1)).block()
        Thread.sleep(1500)
        StepVerifier.create(store().renew("index:contract-d", "owner-1", long)).expectNext(ClaimResult.Lost).verifyComplete()
        StepVerifier.create(store().claim("index:contract-d", "owner-2", long)).expectNext(ClaimResult.Granted).verifyComplete()
    }

    @Test
    fun `a keyspace with no service_lease table names the script to apply`() {
        StepVerifier.create(store("service_lease_absent").claim("index:contract-e", "owner-1", long))
            .expectErrorSatisfies {
                assertThat(it.message).contains("has no service_lease_absent table").contains("keyspace-long.cql")
            }.verify()
    }
}
