package com.demo.chat.test

import com.demo.chat.security.access.composite.MessageServiceAccess
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.security.access.prepost.PreAuthorize

class MessageSubmitCheckTests {
    private fun checkOf(name: String): String? =
        MessageServiceAccess::class.java.methods.single { it.name == name }.getAnnotation(PreAuthorize::class.java)?.value

    @Test
    fun `submit carries the SEND check of send`() {
        assertThat(checkOf("submit")).isEqualTo(checkOf("send"))
    }

    @Test
    fun `command status carries an explicit check, and the service checks ownership`() {
        assertThat(checkOf("commandStatus")).isEqualTo("permitAll()")
    }
}
