package com.demo.chat.test.policy

import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyBearer
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.security.policy.ActionContext
import com.demo.chat.service.security.policy.ActionTrigger
import com.demo.chat.service.security.policy.GrantExpiry
import com.demo.chat.service.security.policy.GrantIntent
import com.demo.chat.service.security.policy.GrantPolicy
import com.demo.chat.service.security.policy.GrantRule
import com.demo.chat.service.security.policy.NoGrantPrincipal
import com.demo.chat.service.security.policy.PrincipalSource
import com.demo.chat.service.security.policy.ShippedGrantPolicies
import com.demo.chat.service.security.policy.TargetSource
import com.demo.chat.test.key.RootKeysFixture
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * The action grant policy model. See `CHAT-xdsetfkf`.
 *
 * The model reads no store. So each test evaluates a policy and reads the
 * intents. `ShippedGrantPolicyTests` in `chat-security` compares the shipped
 * policies with the rows of the shipped writers.
 */
class GrantPolicyTests {

    private val rootKeys: RootKeys<Long> = RootKeysFixture.ofLong(
        mapOf(ChatDomain.USER to USER_ROOT, ChatDomain.MESSAGE_TOPIC to TOPIC_ROOT),
        admin = ADMIN,
        anon = ANON,
    )

    @Test
    fun `the add policy gives the creator the wildcard on the room`() {
        assertThat(ShippedGrantPolicies.ROOM_OWNER.intents(ActionContext(MEMBER, ROOM), rootKeys))
            .containsExactly(GrantIntent(MEMBER, ROOM, "*", GrantExpiry.NONE))
    }

    /** **One action gives two intents.** */
    @Test
    fun `the join policy gives the member SEND and SUBSCRIBE with no expiry`() {
        assertThat(ShippedGrantPolicies.ROOM_JOIN.intents(ActionContext(MEMBER, ROOM), rootKeys)).containsExactly(
            GrantIntent(MEMBER, ROOM, "SEND", GrantExpiry.NONE),
            GrantIntent(MEMBER, ROOM, "SUBSCRIBE", GrantExpiry.NONE),
        )
    }

    @Test
    fun `the leave policy expires SEND and SUBSCRIBE of the member now`() {
        assertThat(ShippedGrantPolicies.ROOM_LEAVE.intents(ActionContext(MEMBER, ROOM), rootKeys)).containsExactly(
            GrantIntent(MEMBER, ROOM, "SEND", GrantExpiry.NOW),
            GrantIntent(MEMBER, ROOM, "SUBSCRIBE", GrantExpiry.NOW),
        )
    }

    /** **The target is the room, and never the domain root of the room.** */
    @Test
    fun `an intent names the room and does not widen to the topic root`() {
        val targets = ShippedGrantPolicies.ALL.flatMap { it.intents(ActionContext(MEMBER, ROOM), rootKeys) }
            .map { it.target }
        assertThat(targets).containsOnly(ROOM).doesNotContain(TOPIC_ROOT)
    }

    @Test
    fun `Anon gets no grant from any shipped policy`() {
        ShippedGrantPolicies.ALL.forEach { policy ->
            assertThat(policy.intents(ActionContext(ANON, ROOM), rootKeys)).describedAs(policy.name).isEmpty()
        }
    }

    @Test
    fun `the User root gets no membership grant`() {
        listOf(ShippedGrantPolicies.ROOM_JOIN, ShippedGrantPolicies.ROOM_LEAVE).forEach { policy ->
            assertThat(policy.intents(ActionContext(USER_ROOT, ROOM), rootKeys)).describedAs(policy.name).isEmpty()
        }
    }

    /** **A caller with no identity owns no room, and that is not an error.** */
    @Test
    fun `an action with no active principal gives no ACTIVE intent`() {
        ShippedGrantPolicies.ALL.forEach { policy ->
            assertThat(policy.intents(ActionContext(null, ROOM), rootKeys)).describedAs(policy.name).isEmpty()
        }
    }

    /**
     * **`ROOT` resolves to the `User` root.** The draft rule
     * `{User{ID=ROOT}, key.id, JOIN, now}` is the example. No shipped policy
     * uses it.
     */
    @Test
    fun `a ROOT rule names the User root, beside an ACTIVE rule`() {
        val policy = GrantPolicy(
            "draftadd", ActionTrigger.ROOM_ADDED,
            listOf(
                GrantRule(PrincipalSource.ACTIVE, TargetSource.ROOM, "*", GrantExpiry.NONE),
                GrantRule(PrincipalSource.ROOT, TargetSource.ROOM, "JOIN", GrantExpiry.NOW),
            ),
            setOf(NoGrantPrincipal.ANON),
        )

        assertThat(policy.intents(ActionContext(null, ROOM), rootKeys))
            .containsExactly(GrantIntent(USER_ROOT, ROOM, "JOIN", GrantExpiry.NOW))
        assertThat(policy.intents(ActionContext(MEMBER, ROOM), rootKeys)).containsExactly(
            GrantIntent(MEMBER, ROOM, "*", GrantExpiry.NONE),
            GrantIntent(USER_ROOT, ROOM, "JOIN", GrantExpiry.NOW),
        )
    }

    /** **An intent carries no storage key.** The writer selects the grant row key. */
    @Test
    fun `an intent holds principal, target, permission and expiry alone`() {
        assertThat(GrantIntent::class.java.declaredFields.map { it.name })
            .containsExactlyInAnyOrder("principal", "target", "permission", "expiry")
        assertThat(KeyBearer::class.java.isAssignableFrom(GrantIntent::class.java)).isFalse()
    }

    @Test
    fun `the shipped policies have distinct names and triggers`() {
        assertThat(ShippedGrantPolicies.ALL.map { it.name }).doesNotHaveDuplicates()
        assertThat(ShippedGrantPolicies.ALL.map { it.trigger })
            .containsExactly(ActionTrigger.ROOM_ADDED, ActionTrigger.ROOM_JOINED, ActionTrigger.ROOM_LEFT)
    }

    @Test
    fun `a subtraction rule is refused`() {
        assertThatThrownBy { policy(ActionTrigger.ROOM_JOINED, GrantRule(PrincipalSource.ACTIVE, TargetSource.ROOM, "-", GrantExpiry.NONE))() }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("cannot remove a permission")
    }

    /** **`*` comes from the room owner rule alone.** A close row needs an owner decision first. */
    @Test
    fun `a wildcard outside the room owner rule is refused`() {
        val refused = listOf(
            policy(ActionTrigger.ROOM_ADDED, GrantRule(PrincipalSource.ROOT, TargetSource.ROOM, "*", GrantExpiry.NOW)),
            policy(ActionTrigger.ROOM_JOINED, GrantRule(PrincipalSource.ACTIVE, TargetSource.ROOM, "*", GrantExpiry.NONE)),
            policy(ActionTrigger.ROOM_ADDED, GrantRule(PrincipalSource.ACTIVE, TargetSource.ROOM, "*", GrantExpiry.NOW)),
            { GrantPolicy("noanon", ActionTrigger.ROOM_ADDED, listOf(GrantRule(PrincipalSource.ACTIVE, TargetSource.ROOM, "*", GrantExpiry.NONE)), setOf()) },
        )
        refused.forEach { build ->
            assertThatThrownBy { build() }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("outside the room owner rule")
        }
    }

    @Test
    fun `a repeated rule is refused`() {
        val rule = GrantRule(PrincipalSource.ACTIVE, TargetSource.ROOM, "SEND", GrantExpiry.NONE)
        assertThatThrownBy { GrantPolicy("twice", ActionTrigger.ROOM_JOINED, listOf(rule, rule.copy(expiry = GrantExpiry.NOW)), setOf()) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("repeats a rule")
    }

    @Test
    fun `a ROOT rule in a policy that refuses the User root is refused`() {
        assertThatThrownBy {
            GrantPolicy(
                "contradiction", ActionTrigger.ROOM_LEFT,
                listOf(GrantRule(PrincipalSource.ROOT, TargetSource.ROOM, "JOIN", GrantExpiry.NOW)),
                setOf(NoGrantPrincipal.USER_ROOT),
            )
        }.isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining("ROOT rule")
    }

    @Test
    fun `a policy with no rule is refused`() {
        assertThatThrownBy { GrantPolicy("empty", ActionTrigger.ROOM_JOINED, listOf(), setOf()) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("at least one rule")
    }

    private fun policy(trigger: ActionTrigger, rule: GrantRule): () -> GrantPolicy =
        { GrantPolicy("probe", trigger, listOf(rule), setOf(NoGrantPrincipal.ANON)) }

    private companion object {
        val USER_ROOT: Key<Long> = Key.root(3L)
        val TOPIC_ROOT: Key<Long> = Key.root(5L)

        val ADMIN: Key<Long> = Key.of(2L, 3L)
        val ANON: Key<Long> = Key.of(1L, 3L)
        val MEMBER: Key<Long> = Key.of(6L, 3L)

        val ROOM: Key<Long> = Key.of(7L, 5L)
    }
}
