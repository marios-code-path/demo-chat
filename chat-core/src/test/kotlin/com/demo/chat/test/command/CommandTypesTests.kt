package com.demo.chat.test.command

import com.demo.chat.domain.KeyVerificationException
import com.demo.chat.domain.NoEffectRefusal
import com.demo.chat.domain.NotFoundException
import com.demo.chat.domain.command.BackendId
import com.demo.chat.domain.command.BackendState
import com.demo.chat.domain.command.BackendStatus
import com.demo.chat.domain.command.CompletionRequirement
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class CommandTypesTests {

    @Test
    fun `a requirement parses backend letters`() {
        assertThat(CompletionRequirement.parse("P,I").backends)
            .containsExactlyInAnyOrder(BackendId.PERSISTENCE, BackendId.INDEX)
    }

    @Test
    fun `a requirement parses none as the empty requirement`() {
        assertThat(CompletionRequirement.parse("none")).isEqualTo(CompletionRequirement.NONE)
        assertThat(CompletionRequirement.NONE.backends).isEmpty()
    }

    @Test
    fun `a requirement refuses an unknown letter and names it`() {
        assertThatThrownBy { CompletionRequirement.parse("P,X") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("'X'")
    }

    @Test
    fun `a pending backend status starts with zero attempts`() {
        assertThat(BackendStatus(BackendState.PENDING).attempts).isZero()
    }

    @Test
    fun `the refusals before a write carry the marker`() {
        assertThat(NotFoundException).isInstanceOf(NoEffectRefusal::class.java)
        assertThat(KeyVerificationException("x")).isInstanceOf(NoEffectRefusal::class.java)
    }
}
