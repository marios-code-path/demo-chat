package com.demo.chat.domain.clock

import com.demo.chat.domain.NodeId

/**
 * One reading of a [VectorClock], with the node that took it.
 *
 * A grant carries a stamp so that a reader can order grants. The clock
 * answers causality. **The origin answers the case the clock cannot.**
 *
 * See `docs/superpowers/specs/2026-09-23-grant-order-clock-design.md`.
 */
data class ClockStamp(val clock: VectorClock, val origin: NodeId) {

    companion object {

        /**
         * A total order over stamps that never contradicts causality.
         *
         * A subtractive grant removes every permission before it, so the word
         * before must hold for every pair. A vector clock alone leaves it
         * undefined for two grants written at once on two nodes.
         *
         * **This reads [VectorClock.total] first.** That sum rises with
         * causality, so a cause always sorts before its effect. The sum is a
         * linear extension of the partial order.
         *
         * **Do not compare causality pairwise and then break a tie by the
         * origin.** That rule is not transitive. Measured on 2026-09-23:
         * `a={1:1}` from node 2, `b={2:1}` from node 1 and `c={1:2}` from
         * node 0 give `b < a`, `a < c` and `c < b`, which is a cycle. A sort
         * can then answer differently for one input, and TimSort can refuse
         * the comparator.
         *
         * Two stamps with one sum are ordered by the node that wrote them,
         * and then by the counts themselves. That last step keeps the order
         * strict, so two stamps compare equal only when they are equal.
         *
         * **Reading the sum is a choice, not a law.** It orders two
         * concurrent stamps by how much each node had seen. One place decides
         * it.
         */
        val ORDER: Comparator<ClockStamp> = Comparator { left, right ->
            val bySum = left.clock.total.compareTo(right.clock.total)
            if (bySum != 0) return@Comparator bySum

            val byOrigin = left.origin.value.compareTo(right.origin.value)
            if (byOrigin != 0) return@Comparator byOrigin

            compareCounts(left.clock, right.clock)
        }

        /** Orders two clocks of one sum by their counts, so the order stays strict. */
        private fun compareCounts(left: VectorClock, right: VectorClock): Int {
            val nodes = (left.counters.keys + right.counters.keys).sorted()

            nodes.forEach { node ->
                val mine = left.counters[node] ?: 0L
                val theirs = right.counters[node] ?: 0L
                if (mine != theirs) return mine.compareTo(theirs)
            }

            return 0
        }
    }
}
