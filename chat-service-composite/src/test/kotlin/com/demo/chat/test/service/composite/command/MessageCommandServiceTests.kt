package com.demo.chat.test.service.composite.command

import com.demo.chat.domain.AccessDeniedException
import com.demo.chat.domain.ByIdRequest
import com.demo.chat.domain.ChatException
import com.demo.chat.domain.CommandStatusRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.MapRequestConverters
import com.demo.chat.domain.MessageSendRequest
import com.demo.chat.domain.MessageSubmitRequest
import com.demo.chat.domain.NotFoundException
import com.demo.chat.domain.command.BackendId
import com.demo.chat.domain.command.CallerOutcome
import com.demo.chat.domain.command.CommandPendingException
import com.demo.chat.domain.command.CompletionRequirement
import com.demo.chat.domain.command.RequestConflictException
import com.demo.chat.domain.command.SenderMismatchException
import com.demo.chat.domain.command.SubmitterUnavailableException
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.service.command.SubmitterIdentity
import com.demo.chat.service.composite.impl.MessagingServiceImpl
import com.demo.chat.service.core.KeyVerifier
import com.demo.chat.test.service.composite.command.MessagingStack.Companion.ROOM
import com.demo.chat.test.service.composite.command.MessagingStack.Companion.SENDER
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import reactor.core.Exceptions
import reactor.core.publisher.Mono
import java.time.Duration

class MessageCommandServiceTests {
    private val stacks = mutableListOf<MessagingStack>()
    private fun stack(
        requirement: CompletionRequirement = CompletionRequirement.parse("P,I"),
        timeout: Duration = Duration.ofSeconds(5),
        useSubmitter: Boolean = true,
    ) = MessagingStack(requirement, timeout, useSubmitter = useSubmitter).also { stacks += it }

    @AfterEach
    fun close() = stacks.forEach { it.close() }

    private fun submit(s: MessagingStack, requestId: String, text: String = "hello") =
        s.service.submit(MessageSubmitRequest(text, ROOM, requestId))

    /** `block()` wraps a checked `ChatException`. This reads the error that the publisher signalled. */
    private fun errorOf(call: Mono<*>): Throwable =
        Exceptions.unwrap(runCatching { call.block() }.exceptionOrNull() ?: error("The call did not fail."))

    @Test
    fun `a submission completes P and I and stores the message under the assigned key`() {
        val s = stack()
        val result = submit(s, "r-1").block()!!
        assertThat(result.outcome).isEqualTo(CallerOutcome.COMPLETED)
        assertThat(s.persistence.added.single().key.id).isEqualTo(result.receipt.messageKey.id)
        assertThat(result.receipt.messageKey.from).isEqualTo(SENDER)
    }

    @Test
    fun `case 3 - None answers Accepted after admission and still exposes a refusal`() {
        val s = stack(CompletionRequirement.NONE)
        assertThat(submit(s, "r-none").block()!!.outcome).isEqualTo(CallerOutcome.ACCEPTED)
        assertThat(errorOf(submit(s, "r-none", "changed"))).isInstanceOf(RequestConflictException::class.java)
    }

    @Test
    fun `case 10 - a timeout answers Pending, and status reports completion later`() {
        val s = stack(timeout = Duration.ofMillis(200))
        s.gatePersistence = true
        val result = submit(s, "r-slow").block()!!
        assertThat(result.outcome).isEqualTo(CallerOutcome.PENDING)
        s.persistenceGate.tryEmitEmpty()
        CommandFixtures.waitUntil {
            s.service.commandStatus(CommandStatusRequest(result.receipt.commandId)).block()!!
                .backends.values.all { it.state.name == "SUCCEEDED" }
        }
    }

    @Test
    fun `case 21 - a None receipt does not imply lookup before P registers the key`() {
        val s = stack(CompletionRequirement.NONE)
        s.gatePersistence = true
        val receipt = submit(s, "r-lookup").block()!!.receipt
        assertThatThrownBy { s.service.messageById(ByIdRequest(receipt.messageKey.id)).block() }
        s.persistenceGate.tryEmitEmpty()
        CommandFixtures.waitUntil { s.registry.rootOf(receipt.messageKey.id).block() != null }
        assertThat(s.service.messageById(ByIdRequest(receipt.messageKey.id)).block()!!.data).isEqualTo("hello")
    }

    @Test
    fun `case 28 - the legacy send rejects another sender and admits nothing`() {
        val s = stack()
        assertThat(errorOf(s.service.send(MessageSendRequest("forged", 999L, ROOM))))
            .isInstanceOf(SenderMismatchException::class.java)
        assertThat(s.runtime.bus.committedMappings()).isZero()
    }

    @Test
    fun `the legacy send answers a plain key on completion`() {
        val s = stack()
        val key = s.service.send(MessageSendRequest("legacy", SENDER, ROOM)).block()!!
        assertThat(key.javaClass.simpleName).isEqualTo("SimpleKey")
        assertThat(s.persistence.added.single().key.id).isEqualTo(key.id)
    }

    @Test
    fun `the legacy send fails with the command ID when its wait ends`() {
        val s = stack(timeout = Duration.ofMillis(200))
        s.gatePersistence = true
        assertThat(errorOf(s.service.send(MessageSendRequest("slow", SENDER, ROOM))))
            .isInstanceOf(CommandPendingException::class.java)
    }

    @Test
    fun `a composition without a submitter refuses submission`() {
        val s = stack(useSubmitter = false)
        assertThat(errorOf(submit(s, "r-nobody"))).isInstanceOf(SubmitterUnavailableException::class.java)
    }

    private fun serviceAs(s: MessagingStack, submitter: SubmitterIdentity<Long>) = MessagingServiceImpl(
        s.index, s.persistence, s.publications, MapRequestConverters()::topicIdToQuery,
        KeyVerifier(s.registry, s.roots), s.runtime.bus, s.runtime.completions, submitter,
        CompletionRequirement.parse("P,I"), Duration.ofSeconds(1),
    )

    @Test
    fun `no identity is refused`() {
        val s = stack()
        val nobody = object : SubmitterIdentity<Long> {
            override fun current(): Mono<Key<Long>> = Mono.empty()
        }
        assertThat(errorOf(serviceAs(s, nobody).submit(MessageSubmitRequest("x", ROOM, "r-empty"))))
            .isSameAs(AccessDeniedException)
    }

    @Test
    fun `review focus 5 - status of another owner's command is not found`() {
        val s = stack()
        val result = submit(s, "r-owned").block()!!
        val stranger = s.registry.register(77L, ChatDomain.USER)
        val asStranger = object : SubmitterIdentity<Long> {
            override fun current(): Mono<Key<Long>> = Mono.just(stranger)
        }
        assertThat(s.service.commandStatus(CommandStatusRequest(result.receipt.commandId)).block()).isNotNull
        assertThat(errorOf(serviceAs(s, asStranger).commandStatus(CommandStatusRequest(result.receipt.commandId))))
            .isSameAs(NotFoundException)
    }

    @Test
    fun `review 1 - an anonymous caller, which has no submitter, submits nothing, sends nothing, and reads no status`() {
        val s = stack()
        val owned = submit(s, "r-owned-by-user").block()!!
        val anonymous = serviceAs(s, object : SubmitterIdentity<Long> {
            override fun current(): Mono<Key<Long>> = Mono.empty()
        })
        val before = s.runtime.bus.committedMappings()
        assertThat(errorOf(anonymous.submit(MessageSubmitRequest("hi", ROOM, "shared-1")))).isSameAs(AccessDeniedException)
        assertThat(errorOf(anonymous.send(MessageSendRequest("hi", CommandFixtures.ROOTS.anon().id, ROOM))))
            .isSameAs(AccessDeniedException)
        assertThat(errorOf(anonymous.commandStatus(CommandStatusRequest(owned.receipt.commandId))))
            .isSameAs(AccessDeniedException)
        assertThat(s.runtime.bus.committedMappings()).isEqualTo(before)
    }

    @Test
    fun `case 15 - a message published before I indexes reaches a listener that starts between`() {
        val s = stack(CompletionRequirement.NONE)
        s.gateIndex = true
        val receipt = submit(s, "r-gap").block()!!.receipt
        CommandFixtures.waitUntil { s.publications.publicationCount(ROOM) == 1 }
        val heard = s.service.listenTopic(ByIdRequest(ROOM)).take(Duration.ofMillis(500)).collectList().block()!!
        assertThat(heard.map { it.key.id }).containsExactly(receipt.messageKey.id)
        s.indexGate.tryEmitEmpty()
    }

    @Test
    fun `case 16 - a message in the history and the live stream reaches the listener once`() {
        val s = stack(CompletionRequirement.NONE)
        s.gatePubsub = true
        val receipt = submit(s, "r-both").block()!!.receipt
        CommandFixtures.waitUntil { s.index.added.isNotEmpty() && s.persistence.added.isNotEmpty() }
        val listening = s.service.listenTopic(ByIdRequest(ROOM)).take(Duration.ofSeconds(1)).collectList().toFuture()
        Thread.sleep(200)
        s.pubsubGate.tryEmitEmpty()
        val heard = listening.get()!!
        assertThat(heard.map { it.key.id }).containsExactly(receipt.messageKey.id)
    }

    @Test
    fun `review focus 3 - a cancel during the history read disposes the live subscription`() {
        val s = stack()
        val subscription = s.service.listenTopic(ByIdRequest(ROOM)).subscribe()
        CommandFixtures.waitUntil { s.pubsub.subscribers.get() == 1 }
        subscription.dispose()
        CommandFixtures.waitUntil { s.pubsub.subscribers.get() == 0 }
    }

    @Test
    fun `a vector failure does not stop U or the P and I requirement`() {
        val s = stack()
        val vector = ScriptedHandler(BackendId.VECTOR) { _, _ -> Mono.error(ChatException("vector down")) }
        assertThat(vector.descriptor.backend.letter).isEqualTo("V")
        val result = submit(s, "r-vector").block()!!
        assertThat(result.outcome).isEqualTo(CallerOutcome.COMPLETED)
        CommandFixtures.waitUntil { s.publications.publicationCount(ROOM) == 1 }
    }
}
