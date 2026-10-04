package com.demo.chat.test.key

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The limit of `VerifiedKey`. See `CHAT-avduuqwp`.
 *
 * **The `trustTypedStore` guard is gone.** That conversion and its one caller,
 * `hasAccessToEntity`, were removed on 2026-10-04 with the core access
 * interfaces. See `CHAT-wgdnjdio`.
 *
 * **These guards read source text,** because a test cannot call the semantic
 * tools. A call through reflection escapes them. The pattern test pins what
 * the patterns match, so a change to a pattern is seen.
 */
class KeyVerifierConstructionTests {

    /** This pattern matches a constructor call with or without type arguments. It also matches a constructor reference. */
    private val constructorCall = Regex("""(\bVerifiedKey\s*(<[^<>()]*>)?\s*\()|(::\s*VerifiedKey\b)""")

    /** The guard excludes `KeyVerifier.kt` alone. Every other file, `VerifiedKey.kt` included, is scanned. */
    private fun constructorOffenders(files: List<Pair<String, String>>): List<String> =
        files.filter { (name, _) -> name != "KeyVerifier.kt" }
            .flatMap { (name, text) -> constructorCall.findAll(text).map { "$name: ${it.value}" }.toList() }

    @Test
    fun `only KeyVerifier constructs a VerifiedKey`() {
        assertThat(constructorOffenders(mainSources().map { it.name to it.readText() })).isEmpty()
    }

    @Test
    fun `a constructor call inside VerifiedKey kt fails the guard`() {
        val helper = """
            class VerifiedKey<T> internal constructor(val key: Key<T>)
            fun <T> unchecked(k: Key<T>) = VerifiedKey(k)
        """.trimIndent()

        assertThat(constructorOffenders(listOf("VerifiedKey.kt" to helper))).containsExactly("VerifiedKey.kt: VerifiedKey(")
        assertThat(constructorOffenders(listOf("KeyVerifier.kt" to helper))).isEmpty()
    }

    @Test
    fun `the guards match every form they must catch`() {
        listOf("VerifiedKey(k)", "VerifiedKey<T>(k)", "VerifiedKey<Long> (k)", "::VerifiedKey").forEach {
            assertThat(constructorCall.containsMatchIn(it)).describedAs(it).isTrue()
        }
        listOf("class VerifiedKey<T> internal constructor(val key: Key<T>)", "fun f(k: VerifiedKey<T>)", "Mono<VerifiedKey<T>>")
            .forEach { assertThat(constructorCall.containsMatchIn(it)).describedAs(it).isFalse() }
    }

    @Test
    fun `the source walk reaches KeyVerifier`() {
        assertThat(mainSources().map { it.name }).contains("KeyVerifier.kt", "VerifiedKey.kt")
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
}
