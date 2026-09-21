package com.demo.chat.test.commands

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.shell.core.autoconfigure.JLineShellAutoConfiguration
import org.springframework.shell.core.autoconfigure.SpringShellAutoConfiguration
import org.springframework.shell.jline.JLineShellRunner

/**
 * The interactive runner is present, and it is present on purpose.
 *
 * **The Spring Shell 4.0.3 starter carries no JLine.** Its published pom
 * brings spring-boot-starter and spring-shell-core-autoconfigure and
 * nothing else, so `spring-shell-jline` is declared explicitly in this
 * module. Drop that declaration and the shell still compiles, still passes
 * every command test, and then starts with no interactive runner.
 *
 * `JLineShellAutoConfiguration` contributes the runner only when the JLine
 * classes are on the classpath. This test asserts the contribution rather
 * than the declaration, so it fails for the reason that matters rather than
 * for a line in a pom.
 *
 * It uses a context runner rather than the shell application context.
 * `ShellContextTests` is the boot test for that, and it is disabled for a
 * reason unrelated to JLine. See CHAT-fxrwtvef.
 */
class JLineShellRunnerTests {

    // SpringShellAutoConfiguration supplies what the JLine half needs,
    // including the user config path provider. Both are what a real shell
    // context loads, so the pair is the honest unit here.
    private val contexts = ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                SpringShellAutoConfiguration::class.java,
                JLineShellAutoConfiguration::class.java,
            )
        )

    @Test
    fun `the JLine runner is contributed when interactive is enabled`() {
        contexts
            .withPropertyValues("spring.shell.interactive.enabled=true")
            .run { context ->
                assertThat(context)
                    .describedAs("the interactive runner that spring-shell-jline supplies")
                    .hasSingleBean(JLineShellRunner::class.java)
            }
    }

    @Test
    fun `the JLine runner stays out when interactive is disabled`() {
        // Every test and container run in this repository sets this to
        // false. A runner that ignored the flag would take the terminal in
        // a build.
        contexts
            .withPropertyValues("spring.shell.interactive.enabled=false")
            .run { context ->
                assertThat(context).doesNotHaveBean(JLineShellRunner::class.java)
            }
    }
}
