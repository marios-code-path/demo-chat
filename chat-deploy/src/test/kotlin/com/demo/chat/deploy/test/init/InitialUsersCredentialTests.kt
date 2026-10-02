package com.demo.chat.deploy.test.init

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.io.ByteArrayOutputStream
import java.io.PrintStream

/**
 * What a blank password does, and what the console shows.
 *
 * **A blank password generates a new credential at every start.** The service
 * writes the generated value to the console one time per account. The owner
 * accepted that an operator who blanks the `Admin` password prints a live
 * administrator credential. See `CHAT-werokcbb`.
 *
 * The encoder is a real `BCryptPasswordEncoder`, so a test proves that the
 * printed text is the text behind the stored hash.
 *
 * **`@ResourceLock` guards `System.out`.** This class replaces the process wide
 * stream, so it must not run beside another class that does. The lock takes
 * effect when parallel execution is on, and it states the requirement when it
 * is off. See `CHAT-werokcbb`.
 */
@ResourceLock("system.out")
class InitialUsersCredentialTests {

    private val console = ByteArrayOutputStream()
    private lateinit var original: PrintStream

    @BeforeEach
    fun captureConsole() {
        original = System.out
        System.setOut(PrintStream(console, true))
    }

    @AfterEach
    fun restoreConsole() {
        System.setOut(original)
    }

    /**
     * The value of the **newest** `Generated password for account '<name>':
     * <value>` line per account. `associate` keeps the last value for a
     * repeated name, so a second start overwrites the first reading.
     */
    private fun generated(): Map<String, String> = console.toString().lines()
        .filter { it.startsWith(GENERATED) }
        .associate { line ->
            val name = line.substringAfter("account '").substringBefore("'")
            name to line.substringAfterLast(": ")
        }

    /**
     * **The printed value is the stored value.** A run that printed a throwaway
     * string would satisfy a weaker test. The hash comparison is the control.
     */
    @Test
    fun `a blank password generates one and stores the printed value`() {
        val fixture = InitialUsersFixture(BCryptPasswordEncoder())
        val roots = fixture.roots()

        fixture.service(
            InitialUsersFixture.properties(
                "Anon" to InitialUsersFixture.user("Anon", "Anon", "_"),
                "Admin" to InitialUsersFixture.user("Admin", "Admin"),
            )
        ).initializeUsers(roots)

        val printed = generated()
        assertThat(printed.keys).containsExactly("Admin")

        val stored = fixture.secretsStore.stored(roots.admin())!!
        assertThat(BCryptPasswordEncoder().matches(printed.getValue("Admin"), stored))
            .describedAs("the printed password matches the stored hash")
            .isTrue()
    }

    /**
     * **A second start replaces the stored credential, and the second printed
     * value is the second stored value.** A test that only compared the two
     * hashes would pass if the service printed a value it never stored.
     */
    @Test
    fun `a second start replaces the stored credential`() {
        val fixture = InitialUsersFixture(BCryptPasswordEncoder())
        val roots = fixture.roots()
        val properties = InitialUsersFixture.properties(
            "Anon" to InitialUsersFixture.user("Anon", "Anon", "_"),
            "Admin" to InitialUsersFixture.user("Admin", "Admin"),
        )

        fixture.service(properties).initializeUsers(roots)
        val first = fixture.secretsStore.stored(roots.admin())!!
        fixture.service(properties).initializeUsers(roots)
        val second = fixture.secretsStore.stored(roots.admin())!!

        assertThat(second).isNotEqualTo(first)
        assertThat(BCryptPasswordEncoder().matches(generated().getValue("Admin"), second))
            .describedAs("the second printed password matches the second stored hash")
            .isTrue()
        assertThat(BCryptPasswordEncoder().matches(generated().getValue("Admin"), first))
            .describedAs("the second printed password does not match the first hash")
            .isFalse()
    }

    /** An explicit password prints no generated line, and it is the stored one. */
    @Test
    fun `an explicit password prints no generated line`() {
        val fixture = InitialUsersFixture(BCryptPasswordEncoder())
        val roots = fixture.roots()

        fixture.service(
            InitialUsersFixture.properties(
                "Anon" to InitialUsersFixture.user("Anon", "Anon", "_"),
                "Admin" to InitialUsersFixture.user("Admin", "Admin", "changeme"),
            )
        ).initializeUsers(roots)

        assertThat(generated()).isEmpty()
        assertThat(BCryptPasswordEncoder().matches("changeme", fixture.secretsStore.stored(roots.admin())!!))
            .describedAs("the explicit password is the stored one")
            .isTrue()
    }

    private companion object {
        const val GENERATED = "Generated password for account '"
    }
}
