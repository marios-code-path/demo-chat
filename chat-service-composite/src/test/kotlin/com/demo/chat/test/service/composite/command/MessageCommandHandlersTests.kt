package com.demo.chat.test.service.composite.command

import com.demo.chat.domain.KeyRootConflictException
import com.demo.chat.domain.Key
import com.demo.chat.domain.NotFoundException
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.pubsub.memory.impl.MemoryTopicPubSubService
import com.demo.chat.service.command.BackendExecutionPolicy
import com.demo.chat.service.command.FailureClass
import com.demo.chat.service.command.SafeRepeatContracts
import com.demo.chat.service.composite.command.handler.MessageIndexHandler
import com.demo.chat.service.composite.command.handler.MessagePersistenceHandler
import com.demo.chat.service.composite.command.handler.MessagePubSubHandler
import com.demo.chat.service.composite.command.publication.RoomPublications
import com.demo.chat.test.key.FakeKeyServices
import com.demo.chat.test.service.composite.FakeMessageIndex
import com.demo.chat.test.service.composite.FakeMessagePersistence
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import reactor.core.Exceptions
import reactor.test.StepVerifier
import java.io.IOException

class MessageCommandHandlersTests {
    private val roots = CommandFixtures.ROOTS
    private val registry = FakeKeyServices.long(roots)
    private val command = CommandFixtures.command()

    @Test
    fun `each handler declares the supported contract of its backend`() {
        val handlers = listOf(
            MessagePersistenceHandler(registry, FakeMessagePersistence()),
            MessageIndexHandler(FakeMessageIndex()),
            MessagePubSubHandler(RoomPublications(MemoryTopicPubSubService<Long, String>())),
        )
        handlers.forEach { SafeRepeatContracts.requireSupported(it.descriptor) }
    }

    @Test
    fun `case 20 - P registers the key and then stores the message`() {
        val persistence = FakeMessagePersistence()
        MessagePersistenceHandler(registry, persistence).handle(command).block()
        assertThat(registry.rootOf(command.message.key.id).block()).isEqualTo(command.message.key.root)
        assertThat(persistence.added.map { it.key.id }).containsExactly(command.message.key.id)
    }

    @Test
    fun `case 20 - a storage failure fails P after registration, and a repeat succeeds`() {
        val failing = FakeMessagePersistence(failure = IOException("store down"))
        assertThatThrownBy { MessagePersistenceHandler(registry, failing).handle(command).block() }
        assertThat(registry.rootOf(command.message.key.id).block()).isEqualTo(command.message.key.root)

        val working = FakeMessagePersistence()
        MessagePersistenceHandler(registry, working).handle(command).block()
        assertThat(working.added).hasSize(1)
    }

    @Test
    fun `case 19 - a root conflict fails P and stores nothing`() {
        registry.register(Key.of(command.message.key.id, roots.of(ChatDomain.USER).id)).block()
        val persistence = FakeMessagePersistence()
        StepVerifier.create(MessagePersistenceHandler(registry, persistence).handle(command))
            .expectError(KeyRootConflictException::class.java)
            .verify()
        assertThat(persistence.added).isEmpty()
        assertThat(BackendExecutionPolicy().classify(KeyRootConflictException(1L, 2L, 3L))).isEqualTo(FailureClass.DEFINITIVE)
    }

    @Test
    fun `I adds the message to the index`() {
        val index = FakeMessageIndex()
        MessageIndexHandler(index).handle(command).block()
        assertThat(index.added.map { it.key.id }).containsExactly(command.message.key.id)
    }

    @Test
    fun `review focus 2 - U on a room that is not open fails definitively`() {
        val handler = MessagePubSubHandler(RoomPublications(MemoryTopicPubSubService<Long, String>()))
        val error = Exceptions.unwrap(runCatching { handler.handle(command).block() }.exceptionOrNull()!!)
        assertThat(error).isSameAs(NotFoundException)
        assertThat(BackendExecutionPolicy().classify(error)).isEqualTo(FailureClass.DEFINITIVE)
    }
}
