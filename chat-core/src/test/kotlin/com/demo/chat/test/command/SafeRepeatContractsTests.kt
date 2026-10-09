package com.demo.chat.test.command

import com.demo.chat.domain.command.BackendId
import com.demo.chat.domain.command.CommandOperation
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.service.command.HandlerDescriptor
import com.demo.chat.service.command.SafeRepeatContract
import com.demo.chat.service.command.SafeRepeatContracts
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class SafeRepeatContractsTests {
    private fun descriptor(contract: SafeRepeatContract?) = HandlerDescriptor(
        BackendId.PERSISTENCE, setOf(ChatDomain.MESSAGE), setOf(CommandOperation.RECORD_MESSAGE), contract,
    )

    @Test
    fun `a supported contract passes`() {
        assertThatCode { SafeRepeatContracts.requireSupported(descriptor(SafeRepeatContracts.MESSAGE_PERSISTENCE)) }
            .doesNotThrowAnyException()
    }

    @Test
    fun `a missing contract fails and names the backend`() {
        assertThatThrownBy { SafeRepeatContracts.requireSupported(descriptor(null)) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("PERSISTENCE")
            .hasMessageContaining("declares no safe-repeat contract")
    }

    @Test
    fun `an unsupported version fails and names both versions`() {
        assertThatThrownBy { SafeRepeatContracts.requireSupported(descriptor(SafeRepeatContract("message-persistence", 2))) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("message-persistence version 2")
            .hasMessageContaining("message-persistence version 1")
    }
}
