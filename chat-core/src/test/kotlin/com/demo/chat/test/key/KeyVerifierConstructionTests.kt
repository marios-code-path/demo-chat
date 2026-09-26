package com.demo.chat.test.key

import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyVerificationException
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.service.core.KeyVerifier
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The limits of `VerifiedKey` and `trustTypedStore`. See `CHAT-avduuqwp`.
 *
 * **These guards read source text,** because a test cannot call the semantic
 * tools. A call through reflection escapes them. The pattern test pins what
 * the patterns match, so a change to a pattern is seen.
 */
class KeyVerifierConstructionTests {

    private val rootKeys = FakeKeyServices.longRoots()
    private val verifier = KeyVerifier(FakeKeyServices.long(rootKeys), rootKeys)

    /** This pattern matches a constructor call with or without type arguments. It also matches a constructor reference. */
    private val constructorCall = Regex("""(\bVerifiedKey\s*(<[^<>()]*>)?\s*\()|(::\s*VerifiedKey\b)""")

    /** This pattern matches a call or a callable reference. */
    private val trustCall = Regex("""(\btrustTypedStore\s*\()|(::\s*trustTypedStore\b)""")

    @Test
    fun `only KeyVerifier constructs a VerifiedKey`() {
        val offenders = mainSources()
            .filter { it.name != "KeyVerifier.kt" && it.name != "VerifiedKey.kt" }
            .flatMap { f -> constructorCall.findAll(f.readText()).map { "${f.name}: ${it.value}" }.toList() }
        assertThat(offenders).isEmpty()
    }

    @Test
    fun `only hasAccessToEntity calls trustTypedStore`() {
        val offenders = mainSources()
            .filter { it.name != "KeyVerifier.kt" }
            .flatMap { f ->
                val text = f.readText()
                trustCall.findAll(text)
                    .filterNot { f.name == "SpringSecurityAccessBrokerService.kt" && enclosingFunction(text, it.range.first) == "hasAccessToEntity" }
                    .map { "${f.name}:${enclosingFunction(text, it.range.first)}" }
                    .toList()
            }
        assertThat(offenders).isEmpty()
    }

    @Test
    fun `the guards match every form they must catch`() {
        listOf("VerifiedKey(k)", "VerifiedKey<T>(k)", "VerifiedKey<Long> (k)", "::VerifiedKey").forEach {
            assertThat(constructorCall.containsMatchIn(it)).describedAs(it).isTrue()
        }
        listOf("class VerifiedKey<T> internal constructor(val key: Key<T>)", "fun f(k: VerifiedKey<T>)", "Mono<VerifiedKey<T>>")
            .forEach { assertThat(constructorCall.containsMatchIn(it)).describedAs(it).isFalse() }
        assertThat(trustCall.containsMatchIn("verifier::trustTypedStore")).isTrue()
        assertThat(trustCall.containsMatchIn("verifier.trustTypedStore(key, domain)")).isTrue()
    }

    @Test
    fun `the source walk reaches KeyVerifier`() {
        assertThat(mainSources().map { it.name }).contains("KeyVerifier.kt", "VerifiedKey.kt")
    }

    @Test
    fun `trustTypedStore accepts an unknown id with the right root`() {
        val unknown = Key.of(424242L, rootKeys.of(ChatDomain.USER).id)
        assertThat(verifier.trustTypedStore(unknown, ChatDomain.USER).key).isEqualTo(unknown)
    }

    @Test
    fun `trustTypedStore refuses a key with another domain root`() {
        val wrong = Key.of(424242L, rootKeys.of(ChatDomain.MESSAGE).id)
        assertThatThrownBy { verifier.trustTypedStore(wrong, ChatDomain.USER) }
            .isInstanceOf(KeyVerificationException::class.java)
    }

    /** Every Kotlin file under the `src/main` tree of each `chat-` module, from the repository root. */
    private fun mainSources(): List<File> {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "pom.xml").exists() && File(it, "chat-core").isDirectory }
        return root.listFiles { f -> f.isDirectory && f.name.startsWith("chat-") }!!
            .map { File(it, "src/main") }
            .filter { it.isDirectory }
            .flatMap { dir -> dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
    }

    /** The name of the nearest `fun <name>` before [offset], at a lower or equal indentation. */
    private fun enclosingFunction(text: String, offset: Int): String {
        val lineStart = text.lastIndexOf('\n', offset) + 1
        val indent = text.substring(lineStart).takeWhile { it == ' ' }.length
        return Regex("""^( *)(?:[a-z]+ )*fun (?:<[^>]*> )?(\w+)""", RegexOption.MULTILINE)
            .findAll(text.substring(0, offset))
            .filter { it.groupValues[1].length <= indent }
            .lastOrNull()?.groupValues?.get(2) ?: "<none>"
    }
}
