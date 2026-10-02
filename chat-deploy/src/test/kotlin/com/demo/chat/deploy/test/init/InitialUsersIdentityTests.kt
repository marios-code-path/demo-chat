package com.demo.chat.deploy.test.init

import com.demo.chat.domain.knownkey.ChatDomain
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * Which initial users become identities, and which stay plain users.
 *
 * `Admin` and `Anon` are the two bootstrap root identities. They live in the
 * closed `ChatIdentity` set in `chat-core`. Every other initial user is a plain
 * user. The MCP adapter account takes that route. See `CHAT-werokcbb`.
 */
class InitialUsersIdentityTests {

    /**
     * **A third initial user is a plain user.** It enters the user store and it
     * enters no `RootKeys` identity. So it holds no `Admin` reach.
     */
    @Test
    fun `a third initial user loads as a plain user`() {
        val fixture = InitialUsersFixture()
        val roots = fixture.roots()

        val keys = fixture.service(
            InitialUsersFixture.properties(
                "Anon" to InitialUsersFixture.user("Anon", "Anon"),
                "Admin" to InitialUsersFixture.user("Admin", "Admin"),
                "Agent" to InitialUsersFixture.user("Agent", "Agent"),
            )
        ).initializeUsers(roots)

        assertThat(keys.keys).containsExactlyInAnyOrder("Anon", "Admin", "Agent")
        assertThat(roots.identities().keys.map { it.wireName })
            .describedAs("the loaded identities")
            .containsExactlyInAnyOrder("Admin", "Anon")
        assertThat(keys["Agent"]).isNotEqualTo(roots.admin())
        assertThat(keys["Agent"]).isNotEqualTo(roots.anon())
    }

    /**
     * **A user name that is also a domain name stays a plain user.** No role
     * row can name it, because `RootKeys.byName` reads the name as a domain
     * first. The shipped rows keep their meaning.
     */
    @Test
    fun `an initial user named after a domain loads as a plain user`() {
        val fixture = InitialUsersFixture()
        val roots = fixture.roots()

        fixture.service(
            InitialUsersFixture.properties(
                "Anon" to InitialUsersFixture.user("Anon", "Anon"),
                "Admin" to InitialUsersFixture.user("Admin", "Admin"),
                "User" to InitialUsersFixture.user("User", "User"),
            )
        ).initializeUsers(roots)

        assertThat(roots.identities()).hasSize(2)
        assertThat(roots.byName("User"))
            .describedAs("the name User still names the User domain root")
            .isEqualTo(roots.of(ChatDomain.USER))
    }

    /** A missing `Admin` key fails the start. The message names the identity. */
    @Test
    fun `a missing Admin identity fails the start`() {
        val fixture = InitialUsersFixture()

        assertThatThrownBy {
            fixture.service(
                InitialUsersFixture.properties(
                    "Anon" to InitialUsersFixture.user("Anon", "Anon"),
                    "Agent" to InitialUsersFixture.user("Agent", "Agent"),
                )
            ).initializeUsers(fixture.roots())
        }.hasMessageContaining("do not name the Admin identity")
    }

    /** A missing `Anon` key fails the start. The message names the identity. */
    @Test
    fun `a missing Anon identity fails the start`() {
        val fixture = InitialUsersFixture()

        assertThatThrownBy {
            fixture.service(
                InitialUsersFixture.properties(
                    "Admin" to InitialUsersFixture.user("Admin", "Admin"),
                    "Agent" to InitialUsersFixture.user("Agent", "Agent"),
                )
            ).initializeUsers(fixture.roots())
        }.hasMessageContaining("do not name the Anon identity")
    }
}
