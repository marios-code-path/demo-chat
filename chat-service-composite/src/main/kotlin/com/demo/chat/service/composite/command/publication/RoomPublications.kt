package com.demo.chat.service.composite.command.publication

import com.demo.chat.domain.Message
import com.demo.chat.domain.command.UncertainOutcomeException
import com.demo.chat.service.core.TopicPubSubService
import reactor.core.Disposable
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import reactor.core.scheduler.Scheduler
import reactor.core.scheduler.Schedulers
import java.util.concurrent.ConcurrentHashMap

class PublicationRecord<T, V>(val commandId: String, val message: Message<T, V>, val sequence: Long)

/** A test seam between `EmitResult.OK` and the bookkeeping commit. Production uses [NONE]. */
interface PublicationBookkeeping {
    fun beforeCommit() {}

    companion object {
        val NONE = object : PublicationBookkeeping {}
    }
}

/** The replay up to the boundary, and the live messages after it. */
class LiveSubscription<T, V>(
    val replay: List<Message<T, V>>,
    val messages: Flux<Message<T, V>>,
    private val live: Disposable,
) {
    fun close() = live.dispose()
}

/**
 * The room coordinator, the publication log, and the repeat markers.
 * Decisions 9 and 13 of the spec.
 *
 * One coordinator task runs each publication and each subscription boundary
 * of a room. A publication prepares its record and its repeat marker before
 * the emission. After `EmitResult.OK`, one write of the room state commits
 * both. So every reader sees both or neither.
 */
class RoomPublications<T : Any, V>(
    private val pubsub: TopicPubSubService<T, V>,
    private val scheduler: Scheduler = Schedulers.boundedElastic(),
    private val bookkeeping: PublicationBookkeeping = PublicationBookkeeping.NONE,
) {
    /** The records and the repeat markers of one room. One write replaces both together. */
    class RoomState<T, V>(val records: List<PublicationRecord<T, V>>, val commandIds: Set<String>)

    private val coordinators = ConcurrentHashMap<T, SerialTaskQueue>()
    private val states = ConcurrentHashMap<T, RoomState<T, V>>()

    private fun <R : Any> onRoom(room: T, task: () -> Mono<R>): Mono<R> =
        coordinators.computeIfAbsent(room) { SerialTaskQueue(scheduler) }.submit(task)

    private fun stateOf(room: T): RoomState<T, V> = states[room] ?: RoomState(emptyList(), emptySet())

    fun publish(commandId: String, message: Message<T, V>): Mono<Void> {
        val room = message.key.dest
        return onRoom(room) {
            val current = stateOf(room)
            if (commandId in current.commandIds) return@onRoom Mono.just(true)
            // Prepared before the emission. The commit below is one write.
            val record = PublicationRecord(commandId, message, current.records.size.toLong())
            val next = RoomState(current.records + record, current.commandIds + commandId)
            pubsub.sendMessage(message).then(Mono.fromCallable {
                try {
                    bookkeeping.beforeCommit()
                    states[room] = next
                } catch (e: Exception) {
                    throw UncertainOutcomeException(
                        "Command $commandId reached the live stream of room $room, and its bookkeeping failed.", e,
                    )
                }
                true
            })
        }.then()
    }

    fun subscribe(room: T): Mono<LiveSubscription<T, V>> = onRoom(room) {
        Mono.fromCallable {
            val buffer = Sinks.many().unicast().onBackpressureBuffer<Message<T, V>>()
            val live = pubsub.listenTo(room).subscribe(
                { buffer.tryEmitNext(it) }, { buffer.tryEmitError(it) }, { buffer.tryEmitComplete() },
            )
            val boundary = stateOf(room).records
            LiveSubscription(boundary.map { it.message }, buffer.asFlux().doFinally { live.dispose() }, live)
        }
    }

    fun isPublished(room: T, commandId: String): Boolean = commandId in stateOf(room).commandIds

    fun publicationCount(room: T): Int = stateOf(room).records.size

    /** One read of the room state, for tests of the visibility boundary. */
    fun snapshot(room: T): RoomState<T, V> = stateOf(room)
}
