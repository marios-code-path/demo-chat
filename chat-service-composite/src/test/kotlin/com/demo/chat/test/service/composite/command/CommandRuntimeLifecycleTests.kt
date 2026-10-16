package com.demo.chat.test.service.composite.command

import com.demo.chat.domain.LeasePhases
import com.demo.chat.service.composite.command.memory.CommandRuntimeLifecycle
import com.demo.chat.service.composite.command.memory.MemoryCommandRuntime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

/** The command runtime stops before the lease guards release. See `CHAT-lswjobhz`. */
class CommandRuntimeLifecycleTests {

    @Test
    fun `stop closes the runtime in the writers phase`() {
        val runtime = mock(MemoryCommandRuntime::class.java)
        val lifecycle = CommandRuntimeLifecycle(runtime)
        lifecycle.start()
        assertThat(lifecycle.isRunning).isTrue()

        lifecycle.stop()

        verify(runtime).close()
        assertThat(lifecycle.isRunning).isFalse()
        assertThat(lifecycle.phase).isEqualTo(LeasePhases.WRITERS)
    }
}
