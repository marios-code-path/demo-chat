package com.demo.chat.test.deploy

import com.demo.chat.config.deploy.init.RootKeyStartup
import com.demo.chat.domain.LeasePhases
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** Pins the start and stop order of the spec. See `CHAT-lswjobhz`. */
class LeasePhaseOrderTests {

    @Test
    fun `the backend, the guards, the writers and the start scan start in that order`() {
        val phases = listOf(
            0, // LettuceConnectionFactory phase, spring-data-redis 4.1.1
            LeasePhases.NODE_ID_GUARD,
            LeasePhases.SERVICE_GUARD,
            LeasePhases.WRITERS,
            RootKeyStartup.PHASE,
            Int.MAX_VALUE - 2048,
        )
        assertThat(phases).isSorted().doesNotHaveDuplicates()
        assertThat(phases.first()).isEqualTo(0)
    }
}
