package com.demo.chat.domain.clock

import com.demo.chat.domain.NodeId
import java.util.Collections

/**
 * A vector clock over node ids.
 *
 * **It answers causality, and it does not answer order.** Two values that
 * neither precede nor follow each other are concurrent, and a vector clock
 * says so rather than choosing between them. [ClockStamp] adds the rule that
 * chooses.
 *
 * The index is `app.nodeid`. That value is validated in 0..1023, it has no
 * default, and a store side lease keeps it unique across deployments that
 * write to one store. So the index of this clock is already unique.
 *
 * A value is immutable. Every operation answers a new value.
 */
class VectorClock(source: Map<Int, Long> = emptyMap()) {

    /**
     * The counts, one per node that has ticked.
     *
     * **This map is a copy and it refuses a write.** A caller that kept the
     * map it passed cannot reach this value, and a caller that casts this
     * value to a mutable map receives an error.
     *
     * **A shared map changes a stamp that was already given out.** The
     * clock that a caller holds, and the order it takes part in, both move
     * under it. That was true before [total] was computed once, and the
     * computed sum then added a second disagreement, because the sum stayed
     * as it was while the counts moved.
     *
     * Measured on 2026-09-23, before the copy existed: a caller changed the
     * map it had passed, and [ClockStamp.ORDER] then answered that a clock
     * came before its own cause.
     *
     * **A count of zero is not stored.** It reads the same as an absent
     * count, and two values that compare equal must be equal.
     */
    val counters: Map<Int, Long>

    /**
     * The sum of every count.
     *
     * **This value rises with causality.** If this clock comes before
     * another, then every count is lower or equal and one count is lower, so
     * the sum is lower. [ClockStamp.ORDER] reads it for that reason.
     *
     * It is computed once, here, so that a sort reads it without adding the
     * counts again for every comparison. That is safe because the map cannot
     * change.
     */
    val total: Long

    init {
        // **Copy first, then read only the copy.** A caller can pass a map
        // that another thread is changing. A check that read the source and a
        // copy that read it again could disagree, and the value that this
        // clock kept would be the one that no check had seen.
        val copied = LinkedHashMap(source)

        copied.forEach { (node, count) ->
            require(node in NodeId.MIN..NodeId.MAX) { indexMessage(node) }
            require(count >= 0L) { countMessage(node, count) }
        }

        copied.values.removeIf { it == 0L }

        counters = Collections.unmodifiableMap(copied)

        total = counters.entries.fold(0L) { carried, entry ->
            add(carried, entry.value) { overflowMessage("The counts of this clock") }
        }
    }

    override fun equals(other: Any?): Boolean = other is VectorClock && other.counters == counters

    override fun hashCode(): Int = counters.hashCode()

    override fun toString(): String = "VectorClock(counters=$counters)"

    /** The count this clock holds for one node, or zero. */
    fun countOf(node: NodeId): Long = counters[node.value] ?: 0L

    /**
     * A new clock with the count of one node raised by one.
     *
     * **It refuses to wrap.** A count that wrapped would read as lower than
     * the count before it, and a cause would then sort after its effect.
     */
    fun tick(node: NodeId): VectorClock {
        val raised = add(countOf(node), 1L) { overflowMessage("The count of node ${node.value}") }

        return VectorClock(counters + (node.value to raised))
    }

    /** A new clock that holds the higher count of each node. */
    fun merge(other: VectorClock): VectorClock {
        val nodes = counters.keys + other.counters.keys

        return VectorClock(
            nodes.associateWith { node ->
                maxOf(counters[node] ?: 0L, other.counters[node] ?: 0L)
            }
        )
    }

    /**
     * How this clock stands against another.
     *
     * `BEFORE` and `AFTER` state causality. `CONCURRENT` states that neither
     * caused the other, **which is not a tie and not an error.**
     */
    fun relate(other: VectorClock): Relation {
        val nodes = counters.keys + other.counters.keys
        var anyLower = false
        var anyHigher = false

        nodes.forEach { node ->
            val mine = counters[node] ?: 0L
            val theirs = other.counters[node] ?: 0L
            if (mine < theirs) anyLower = true
            if (mine > theirs) anyHigher = true
        }

        return when {
            !anyLower && !anyHigher -> Relation.EQUAL
            anyLower && !anyHigher -> Relation.BEFORE
            !anyLower && anyHigher -> Relation.AFTER
            else -> Relation.CONCURRENT
        }
    }

    enum class Relation { BEFORE, AFTER, EQUAL, CONCURRENT }

    private companion object {

        /** Adds, and refuses to wrap. */
        inline fun add(left: Long, right: Long, message: () -> String): Long =
            try {
                Math.addExact(left, right)
            } catch (overflow: ArithmeticException) {
                throw IllegalArgumentException(message(), overflow)
            }

        fun overflowMessage(subject: String): String =
            "$subject would pass ${Long.MAX_VALUE}. A count that wrapped would " +
                "read as lower than the count before it, and a cause would sort " +
                "after its effect."

        fun indexMessage(node: Int): String =
            "A clock is indexed by app.nodeid, which is an integer in " +
                "${NodeId.MIN}..${NodeId.MAX}. Got: $node"

        fun countMessage(node: Int, count: Long): String =
            "A count never falls, so it is never negative. Node $node holds $count"
    }
}
