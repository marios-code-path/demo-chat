package com.demo.chat.test.service.composite.command

import com.demo.chat.domain.MessageImportRequest
import com.demo.chat.domain.command.CommandPendingException
import com.demo.chat.service.command.ImportedMessageId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import reactor.core.Exceptions
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** The import route over `UUID` keys. It derives the message id and reads it directly. See `CHAT-bwzjstoh`. */
class UuidImportServiceTests {
    private val stacks = mutableListOf<UuidImportStack>()

    private fun stack(timeout: Duration = Duration.ofSeconds(5)) = UuidImportStack(timeout).also { stacks += it }

    @AfterEach
    fun close() = stacks.forEach { it.close() }

    /** `block()` wraps a checked `ChatException`. This reads the error that the publisher signalled. */
    private fun errorOf(call: Mono<*>): Throwable =
        Exceptions.unwrap(runCatching { call.block() }.exceptionOrNull() ?: error("The call did not fail."))

    private val turn = UUID.fromString("5f0c2a8e-3d4b-4c1a-9e7f-0a1b2c3d4e5f")

    private fun request(s: UuidImportStack, text: String = "imported") =
        MessageImportRequest(text, s.sender.id, s.room.id, Instant.parse("2026-10-08T10:00:00Z"), s.sourceId(turn))

    @Test
    fun `an import stores the message under the derived id`() {
        val s = stack()
        val stored = s.admin.importMessage(request(s)).block()!!

        val derived = ImportedMessageId.derive(s.sourceId(turn), s.room.id)
        assertThat(stored.key.id).isEqualTo(derived)
        assertThat(s.persistence.added.single().key.id).isEqualTo(derived)
    }

    @Test
    fun `a rerun of one source id reads the stored message and submits nothing`() {
        val s = stack()
        val first = s.admin.importMessage(request(s)).block()!!
        assertThat(s.bus.submissions.get()).isEqualTo(1)
        assertThat(s.runtime.bus.committedMappings()).isEqualTo(1)

        val rerun = s.admin.importMessage(request(s)).block()!!

        assertThat(rerun.key.id).isEqualTo(first.key.id)
        assertThat(rerun.data).isEqualTo("imported")
        assertThat(s.bus.submissions.get()).isEqualTo(1)
        assertThat(s.runtime.bus.committedMappings()).isEqualTo(1)
        assertThat(s.persistence.added).hasSize(1)
    }

    // The source id names the message, so the stored message answers. A text
    // match would miss this rerun, and the bus would refuse the changed intent.
    @Test
    fun `a rerun with changed text answers the stored message`() {
        val s = stack()
        val first = s.admin.importMessage(request(s, "original")).block()!!

        val rerun = s.admin.importMessage(request(s, "edited")).block()!!

        assertThat(rerun.key.id).isEqualTo(first.key.id)
        assertThat(rerun.data).isEqualTo("original")
        assertThat(s.bus.submissions.get()).isEqualTo(1)
    }

    // Moved from MessageCommandServiceTests, which runs a Long stack. CHAT-bwzjstoh.
    @Test
    fun `an import fails with the command ID when its wait ends`() {
        val s = stack(timeout = Duration.ofMillis(200))
        s.gatePersistence = true
        assertThat(errorOf(s.admin.importMessage(request(s, "slow")))).isInstanceOf(CommandPendingException::class.java)
    }
}
