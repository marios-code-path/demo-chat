package com.demo.chat.test.command

import com.demo.chat.domain.ChatException
import com.demo.chat.domain.DefinitiveRefusalException
import com.demo.chat.domain.NotFoundException
import com.demo.chat.domain.command.UncertainOutcomeException
import com.demo.chat.service.command.BackendExecutionPolicy
import com.demo.chat.service.command.FailureClass
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.io.IOException
import java.time.Duration
import java.util.concurrent.TimeoutException

class BackendExecutionPolicyTests {
    private val policy = BackendExecutionPolicy()

    @Test
    fun `five attempts include the first attempt`() {
        assertThat(policy.initialAttempts).isEqualTo(5)
        assertThat(policy.retriesAfterFirst).isEqualTo(4L)
    }

    @Test
    fun `the starting values match the spec`() {
        assertThat(policy.firstBackoff).isEqualTo(Duration.ofMillis(100))
        assertThat(policy.recoveryInterval).isEqualTo(Duration.ofSeconds(30))
    }

    @Test
    fun `a timeout and a connection error are transient`() {
        assertThat(policy.classify(TimeoutException("slow"))).isEqualTo(FailureClass.TRANSIENT)
        assertThat(policy.classify(IOException("reset"))).isEqualTo(FailureClass.TRANSIENT)
    }

    @Test
    fun `a wrapped driver timeout is transient`() {
        val wrapped = RuntimeException("data access", TimeoutException("driver"))
        assertThat(policy.classify(wrapped)).isEqualTo(FailureClass.TRANSIENT)
    }

    @Test
    fun `an uncertain outcome is uncertain even when it wraps a connection error`() {
        val error = UncertainOutcomeException("bookkeeping", IOException("reset"))
        assertThat(policy.classify(error)).isEqualTo(FailureClass.UNCERTAIN)
    }

    @Test
    fun `a refusal before any effect is definitive`() {
        assertThat(policy.classify(DefinitiveRefusalException("refused"))).isEqualTo(FailureClass.DEFINITIVE)
        assertThat(policy.classify(NotFoundException)).isEqualTo(FailureClass.DEFINITIVE)
    }

    @Test
    fun `an unknown error is uncertain, because it can follow an effect`() {
        assertThat(policy.classify(IllegalStateException("bug"))).isEqualTo(FailureClass.UNCERTAIN)
        assertThat(policy.classify(ChatException("unmarked"))).isEqualTo(FailureClass.UNCERTAIN)
    }

    @Test
    fun `a policy refuses fewer than one attempt`() {
        assertThatThrownBy { BackendExecutionPolicy(initialAttempts = 0) }
            .hasMessageContaining("at least one attempt")
    }
}
