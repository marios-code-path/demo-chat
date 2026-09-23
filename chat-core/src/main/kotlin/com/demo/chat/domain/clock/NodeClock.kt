package com.demo.chat.domain.clock

import com.demo.chat.domain.NodeId
import java.util.concurrent.atomic.AtomicReference

/**
 * The clock of one process.
 *
 * [tick] is atomic. Two threads that tick at once receive two different
 * counts, and neither reading is lost.
 *
 * **This clock never reads a wall clock.** The order it gives does not move
 * when a host clock moves. That is why it replaces the order that `key.id`
 * carried, because `SnowflakeGenerator` builds an id from a wall clock and a
 * uuid key carries no order at all.
 */
class NodeClock(private val node: NodeId, start: VectorClock = VectorClock()) {

    private val current = AtomicReference(start)

    /** The clock as it stands, without moving it. */
    fun read(): VectorClock = current.get()

    /** Raises the count of this node, and answers the new reading. */
    fun tick(): ClockStamp = ClockStamp(current.updateAndGet { it.tick(node) }, node)

    /**
     * Takes account of a clock that another node wrote.
     *
     * A reader calls this when it reads a stamp from the store. The local
     * clock then holds the higher count of every node, so the next [tick]
     * follows what this process has seen.
     */
    fun observe(other: VectorClock): VectorClock = current.updateAndGet { it.merge(other) }
}
