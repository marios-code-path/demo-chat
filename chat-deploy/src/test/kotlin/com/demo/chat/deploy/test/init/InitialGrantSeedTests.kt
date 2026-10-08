package com.demo.chat.deploy.test.init

import com.demo.chat.config.deploy.init.InitalRoles
import com.demo.chat.config.deploy.init.RoleDefinition
import com.demo.chat.config.deploy.init.UserInitializationProperties
import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.Key
import com.demo.chat.domain.knownkey.ChatDomain
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * **The initial grants are a seed, not a reconcile.** See `CHAT-ghwtzgjp`.
 *
 * - A start writes an initial grant only when the store holds no row with the
 *   same principal, target and permission. Expired and muted rows count.
 * - A start keeps one row of each initial grant. Where the store holds copies,
 *   it keeps the row that the summarizer selects, and it removes the others.
 *   That is the highest key id, because the copies tie on the rank.
 *
 * So a restart adds no row, and it does not undo a revoke.
 */
class InitialGrantSeedTests {

    private val fixture = InitialUsersFixture()

    private val roots = fixture.roots()

    private val properties = UserInitializationProperties(
        "noop",
        InitalRoles(
            arrayOf(
                RoleDefinition("User", "Message", "SEND"),
                RoleDefinition("Anon", "User", "FIND"),
            )
        ),
        mapOf(
            "Anon" to InitialUsersFixture.user("Anon", "Anon", "_"),
            "Admin" to InitialUsersFixture.user("Admin", "Admin", "changeme"),
        ),
    )

    private fun start() = fixture.service(properties).initializeUsers(roots)

    private fun userSend(): List<AuthMetadata<Long>> =
        fixture.authorizationService.stored(roots.of(ChatDomain.USER), roots.of(ChatDomain.MESSAGE), "SEND")

    /**
     * A second start writes no row. It fails when the start writes each
     * initial grant again, which is the defect of `CHAT-ghwtzgjp`.
     */
    @Test
    fun `a second start writes no initial grant again`() {
        start()
        val first = fixture.authorizationService.stored().map { it.key }.toSet()

        start()

        assertThat(fixture.authorizationService.stored().map { it.key }.toSet())
            .describedAs("the stored grant keys after two starts")
            .isEqualTo(first)
        assertThat(userSend()).hasSize(1)
    }

    @Test
    fun `seed grants never expire`() {
        start()

        assertThat(fixture.authorizationService.stored().map { it.expires })
            .describedAs("the expiry of every seeded grant")
            .containsOnly(0L)
    }

    /**
     * An operator revokes an initial grant by expiring its row. A restart
     * keeps the revoke. It fails when the seed check reads live rows only.
     */
    @Test
    fun `a revoked initial grant stays revoked across a restart`() {
        start()
        val revoked = expired(userSend().single())
        fixture.authorizationService.seed(revoked)

        start()

        assertThat(userSend())
            .describedAs("the User SEND rows after the restart")
            .singleElement()
            .satisfies({ row ->
                assertThat(row.key).isEqualTo(revoked.key)
                assertThat(row.expires).isEqualTo(revoked.expires)
            })
    }

    /**
     * Copies that earlier starts wrote collapse to the row the summarizer
     * selects. It fails when the start leaves the copies, or when it keeps a
     * row that the summarizer does not select.
     */
    @Test
    fun `a start collapses copies to the row the summarizer selects`() {
        val principal = roots.of(ChatDomain.USER)
        val target = roots.of(ChatDomain.MESSAGE)
        val copies = (1..3).map {
            fixture.authorizationService.seed(AuthMetadata.create(placeholder(), principal, target, "SEND", false, 0L))
        }
        val selected = copies.maxBy { it.key.id }

        start()

        assertThat(userSend())
            .describedAs("the User SEND rows after the start")
            .singleElement()
            .satisfies({ row -> assertThat(row.key).isEqualTo(selected.key) })
        assertThat(fixture.authorizationService.writes)
            .describedAs("a User SEND write")
            .noneMatch { it.principal == principal && it.target == target && it.permission == "SEND" }
        assertThat(fixture.authorizationService.removals)
            .containsExactlyInAnyOrderElementsOf(copies.map { it.key } - selected.key)
    }

    /** A grant outside the initial roles keeps its copies. The cleanup reaches initial grants alone. */
    @Test
    fun `a start leaves the rows of other grants alone`() {
        val principal = roots.of(ChatDomain.USER)
        val target = roots.of(ChatDomain.MESSAGE_TOPIC)
        repeat(2) {
            fixture.authorizationService.seed(AuthMetadata.create(placeholder(), principal, target, "PUBLISH", false, 0L))
        }

        start()

        assertThat(fixture.authorizationService.stored(principal, target, "PUBLISH")).hasSize(2)
        assertThat(fixture.authorizationService.removals).isEmpty()
    }

    private fun placeholder(): Key<Long> = Key.empty(0L, roots.of(ChatDomain.AUTH_METADATA).id)

    private fun expired(row: AuthMetadata<Long>): AuthMetadata<Long> =
        AuthMetadata.create(row.key, row.principal, row.target, row.permission, row.mute, 1L)
}
