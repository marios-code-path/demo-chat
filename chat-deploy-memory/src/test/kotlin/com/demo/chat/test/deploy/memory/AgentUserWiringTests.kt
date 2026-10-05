package com.demo.chat.test.deploy.memory

import com.demo.chat.ChatApp
import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.domain.ByStringRequest
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.composite.ChatUserService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import java.time.Duration

/**
 * The MCP adapter account, as a deployment creates it from the shipped file.
 *
 * **The agent is a plain user.** It holds no `ChatIdentity`, so it takes no
 * `Admin` reach. `InitialUsersService` writes one `*` row per domain root for
 * the `Admin` key, and the agent key is not that key.
 *
 * **The handle is the match key.** `AgentIdentityLifecycle` resolves
 * each `app.security.agents[n].username` through `ChatUserService.findByUsername`, and it
 * requires exactly one answer. The deployment docs name `Agent`, which is the
 * handle this test reads.
 *
 * The memory deployment claims no node id. See docs/NODEID-CLAIM.md.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [ChatApp::class])
@TestPropertySource(
    properties = [
        "spring.config.additional-location=classpath:/config/logging.yml,classpath:/config/management-defaults.yml,classpath:/config/userinit.yml",
        "spring.application.name=test-deployment-agent-user", "app.server.proto=rsocket",
        "server.port=0", "spring.rsocket.server.port=0", "app.key.type=long", "app.nodeid=1",
        "app.service.core.key=memory",
        "app.service.core.pubsub=memory", "app.service.core.index=lucene", "app.service.core.persistence=memory",
        "app.service.core.secrets=memory", "app.service.composite", "app.service.composite.auth=true",
        "app.controller.key", "app.controller.persistence", "app.controller.index",
        "app.controller.user", "app.controller.message", "app.controller.topic", "app.controller.pubsub",
        "app.service.security.userdetails", "app.users.create=true"
    ]
)
class AgentUserWiringTests {

    /**
     * The composite user service, by the name its own configuration gives it.
     *
     * **The qualifier is not decoration.** The mounted composite controller
     * `userServiceController` is also a `ChatUserService<Long>`, so an
     * unqualified injection is ambiguous. `AgentSecurityConfiguration` names
     * the same qualifier for the same reason.
     */
    @Autowired
    @Qualifier("userService")
    lateinit var users: ChatUserService<Long>

    @Autowired
    lateinit var stores: PersistenceServiceBeans<Long, String>

    @Autowired
    lateinit var rootKeys: RootKeys<Long>

    private val timeout = Duration.ofSeconds(10)

    /** The agent handle answers exactly one user. That is the resolution rule. */
    @Test
    fun `the agent handle answers exactly one user`() {
        val matches = users.findByUsername(ByStringRequest("Agent")).collectList().block(timeout)!!

        assertThat(matches).describedAs("the users named Agent").hasSize(1)
    }

    /**
     * **The agent key differs from both identities.** A key that equalled the
     * `Admin` key would carry every right of the administrator.
     */
    @Test
    fun `the agent key differs from the Admin and Anon keys`() {
        val agent = agentKey()

        assertThat(agent).isNotEqualTo(rootKeys.admin())
        assertThat(agent).isNotEqualTo(rootKeys.anon())
        assertThat(rootKeys.identities()).describedAs("the loaded identities").hasSize(2)
    }

    /**
     * **No grant row names the agent as its principal.** The agent holds the
     * floor that every identity holds, and nothing more.
     */
    @Test
    fun `no grant row names the agent as its principal`() {
        val principals = stores.authMetaPersistence().all().collectList().block(timeout)!!
            .map { it.principal }
            .toSet()

        assertThat(principals).describedAs("the grant principals").doesNotContain(agentKey())
        assertThat(principals).describedAs("the grant principals").containsExactlyInAnyOrder(
            rootKeys.admin(), rootKeys.anon(), rootKeys.of(ChatDomain.USER)
        )
    }

    /**
     * The agent is a plain user, so no `ChatIdentity` names it. A name that
     * resolved here would let a role row address it.
     */
    @Test
    fun `the agent name is not an identity`() {
        assertThat(rootKeys.byName("Agent")).describedAs("the Agent name in the root key set").isNull()
    }

    private fun agentKey() =
        users.findByUsername(ByStringRequest("Agent")).blockFirst(timeout)!!.key
}
