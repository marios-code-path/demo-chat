package com.demo.chat

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.lang.reflect.Modifier

/**
 * Pins the entry point that a native image needs. See `CHAT-arcqfjuc`.
 *
 * In AOT mode, `SpringApplication` names the initializer after the class of
 * the first stack frame with a method named `main`. AOT generates for the
 * class that the build names. A companion `main` puts `ChatApp$Companion`
 * in that frame, so the two names differ and the native image does not start.
 */
class EntryPointShapeTests {

    @Test
    fun `the entry class holds a static main`() {
        val main = Class.forName("com.demo.chat.ChatAppKt")
            .getDeclaredMethod("main", Array<String>::class.java)
        assertThat(Modifier.isStatic(main.modifiers)).isTrue()
        assertThat(Modifier.isPublic(main.modifiers)).isTrue()
    }

    @Test
    fun `ChatApp holds no companion object`() {
        assertThat(ChatApp::class.java.declaredClasses.map { it.simpleName })
            .doesNotContain("Companion")
    }
}
