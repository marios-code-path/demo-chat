package com.demo.chat.test.deploy.memory

import com.demo.chat.ChatApp
import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.domain.knownkey.RootKeys
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import java.time.Duration

/**
 * The Admin wildcard, on every domain root, as a deployed composition writes it.
 *
 * **The owner decided this rule on 2026-10-01.** Every root carries a `*` row
 * for the Admin identity, and `InitialUsersService` writes one row per loaded
 * domain. The loop reads the loaded domain set, so a root that a later release
 * adds receives its own row without a second edit.
 *
 * **The shipped `userinit.yml` cannot express this rule.** A role definition
 * names one user and one target. So the file carries `{Admin, Admin, '*'}`
 * alone, and the per-root rows are generated. This test pins the generated set
 * beside that shipped row, so neither half hides the other.
 *
 * **Admin is not in the actor set of any other caller.** A query carries the
 * `Anon` key, the `User` root and the caller. So these rows reach the Admin
 * identity alone, and the shipped authorization matrix does not move.
 *
 * The memory deployment claims no node id. See docs/NODEID-CLAIM.md.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [ChatApp::class])
@TestPropertySource(
    properties = [
        "spring.config.additional-location=classpath:/config/logging.yml,classpath:/config/management-defaults.yml,classpath:/config/userinit.yml",
        "spring.application.name=test-deployment-admin-root-grant", "app.server.proto=rsocket",
        "server.port=0", "spring.rsocket.server.port=0", "app.key.type=long", "app.nodeid=1",
        "app.service.core.key=memory",
        "app.service.core.pubsub=memory", "app.service.core.index=lucene", "app.service.core.persistence=memory",
        "app.service.core.secrets=memory", "app.service.composite", "app.service.composite.auth",
        "app.controller.key", "app.controller.persistence", "app.controller.index",
        "app.controller.user", "app.controller.message", "app.controller.topic", "app.controller.pubsub",
        "app.service.security.userdetails", "app.users.create=true"
    ]
)
class AdminRootGrantWiringTests {

    @Autowired
    lateinit var stores: PersistenceServiceBeans<Long, String>

    @Autowired
    lateinit var rootKeys: RootKeys<Long>

    private val timeout = Duration.ofSeconds(10)

    /**
     * One row per domain root, plus the shipped `{Admin, Admin, '*'}` row.
     * Nothing else names the Admin identity as its principal.
     */
    @Test
    fun `the Admin identity holds the wildcard on every domain root`() {
        val rows = adminRows()

        assertThat(rows.map { it.target }.toSet())
            .describedAs("the targets that Admin holds the wildcard on")
            .containsExactlyInAnyOrderElementsOf(rootKeys.domains().values + rootKeys.admin())

        assertThat(rows.map { it.permission }.toSet())
            .describedAs("the permission of every Admin row")
            .containsExactly("*")

        assertThat(rows.map { it.expires }.toSet())
            .describedAs("the expiry of every Admin row")
            .containsExactly(0L)
    }

    /**
     * **The row count is the domain count plus one.** A row that a domain root
     * carries twice, or a domain root that no row names, both fail here. The
     * first assertion reads a set, so it cannot see a duplicate.
     */
    @Test
    fun `every domain root carries exactly one Admin row`() {
        val rows = adminRows()

        assertThat(rows)
            .describedAs("the Admin rows")
            .hasSize(rootKeys.domains().size + 1)
    }

    private fun adminRows() = stores.authMetaPersistence()
        .all()
        .collectList()
        .block(timeout)!!
        .filter { it.principal == rootKeys.admin() }
}
