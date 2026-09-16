package com.demo.chat.test.vector

import com.demo.chat.config.DefaultChatJacksonModules
import com.demo.chat.domain.Key
import com.demo.chat.service.vector.VectorIndexPhase
import com.demo.chat.service.vector.VectorIndexStatus
import com.demo.chat.service.vector.VectorIndexTriggerResult
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

/**
 * The trigger answer carries accepted beside the status. The read answer does
 * not carry accepted at all.
 *
 * A rejected trigger and an accepted one both report running=true, because
 * another run holds the claim in the first case. So a reader cannot tell them
 * apart from the status. See CHAT-cxduiwjj.
 */
class VectorIndexTriggerResultWireTests {

    private val mapper: ObjectMapper = ObjectMapper()
        .findAndRegisterModules()
        .apply { registerModules(DefaultChatJacksonModules().allModules()) }

    private val status = VectorIndexStatus<Long>(
        phase = VectorIndexPhase.REBUILDING,
        activeJob = Key.funKey(500L),
    )

    @Test
    fun `the trigger answer carries accepted beside the status`() {
        val json = mapper.writeValueAsString(VectorIndexTriggerResult(true, status))

        Assertions.assertThat(json).contains("\"accepted\":true")
        Assertions.assertThat(json).contains("\"phase\":\"REBUILDING\"")
    }

    @Test
    fun `a rejected trigger carries the same status shape`() {
        val json = mapper.writeValueAsString(VectorIndexTriggerResult(false, status))

        Assertions.assertThat(json).contains("\"accepted\":false")
        Assertions.assertThat(json).contains("\"running\":true")
    }

    @Test
    fun `the status alone carries no accepted field`() {
        // The read operation answers with this type. A trigger only field
        // there would answer a question that a read never asks.
        val json = mapper.writeValueAsString(status)

        Assertions.assertThat(json).doesNotContain("accepted")
    }

    @Test
    fun `the status keeps its derived flags on the wire`() {
        // The launch gate and the operator guide both read status.running, and
        // the recall answer reads complete. A JsonIgnore on either derived flag
        // would break every reader, and no other test would see it.
        //
        // This class writes and never reads. VectorIndexStatus does not decode,
        // because running and complete are derived and no constructor takes
        // them. Nothing in this repository decodes one.
        val json = mapper.writeValueAsString(VectorIndexTriggerResult(true, status))

        Assertions.assertThat(json).contains("\"running\":true")
        Assertions.assertThat(json).contains("\"complete\":false")
    }
}
