package com.demo.chat.test.service.composite.command

import com.demo.chat.domain.Message
import com.demo.chat.domain.SimpleMessageKey
import com.demo.chat.domain.command.AcceptedCommand
import com.demo.chat.domain.command.BackendId
import com.demo.chat.domain.command.CommandOperation
import com.demo.chat.domain.command.Receipt
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.service.command.DomainCommandHandler
import com.demo.chat.service.command.HandlerDescriptor
import com.demo.chat.service.command.SafeRepeatContracts
import com.demo.chat.test.key.FakeKeyServices
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

object CommandFixtures {
    val ROOTS = FakeKeyServices.longRoots()
    val PIU = setOf(BackendId.PERSISTENCE, BackendId.INDEX, BackendId.PUBSUB)
    val SENT_AT: Instant = Instant.parse("2026-10-07T00:00:00Z")

    fun command(
        commandId: String = "c-1",
        room: Long = 100L,
        obligations: Set<BackendId> = PIU,
        text: String = "hello",
        messageId: Long = 1L,
    ): AcceptedCommand<Long, String> {
        val key = SimpleMessageKey(messageId, ROOTS.of(ChatDomain.MESSAGE).id, 10L, room, SENT_AT)
        return AcceptedCommand(
            commandId, 10L, "req-$commandId", key.root, CommandOperation.RECORD_MESSAGE, room, 1,
            Message.create(key, text, true), obligations, 1,
        )
    }

    fun receipt(command: AcceptedCommand<Long, String>) = Receipt(command.commandId, command.message.key)

    fun descriptor(backend: BackendId) = HandlerDescriptor(
        backend, setOf(ChatDomain.MESSAGE), setOf(CommandOperation.RECORD_MESSAGE), SafeRepeatContracts.SUPPORTED.getValue(backend),
    )

    fun waitUntil(timeout: Duration = Duration.ofSeconds(5), condition: () -> Boolean) {
        val end = System.nanoTime() + timeout.toNanos()
        while (!condition()) {
            check(System.nanoTime() < end) { "The condition did not hold within $timeout." }
            Thread.sleep(10)
        }
    }
}

/** A handler that records each call. [script] receives the command and the attempt number, from 1. */
class ScriptedHandler(
    backend: BackendId,
    private val script: (AcceptedCommand<Long, String>, Int) -> Mono<Void> = { _, _ -> Mono.empty() },
) : DomainCommandHandler<Long, String> {
    override val descriptor = CommandFixtures.descriptor(backend)
    val calls = CopyOnWriteArrayList<AcceptedCommand<Long, String>>()

    override fun handle(command: AcceptedCommand<Long, String>): Mono<Void> = Mono.defer {
        calls.add(command)
        script(command, calls.count { it.commandId == command.commandId })
    }

    fun attemptsOf(commandId: String): Int = calls.count { it.commandId == commandId }
}
