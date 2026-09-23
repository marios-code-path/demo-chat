package com.demo.chat.domain.clock

import com.demo.chat.domain.NodeId

/**
 * One reading of a [VectorClock], with the node that took it.
 *
 * A grant carries a stamp so that a reader can order grants. The clock
 * answers causality. **The origin answers the case the clock cannot.**
 *
 * See `docs/superpowers/specs/2026-09-23-operation-policy-draft.md`.
 */
data class ClockStamp(val clock: VectorClock, val origin: NodeId) {

    companion object {

        /**
         * A total order over stamps.
         *
         * Causality decides first. **Two concurrent stamps are ordered by the
         * node that wrote them**, which is deterministic because a node id is
         * unique across the deployments that write to one store.
         *
         * A total order is required, because a subtractive grant removes
         * every permission before it. A partial order would leave that word
         * undefined for two grants written at once on two nodes.
         *
         * The origin rule is a choice, not a law. It is stated here so that
         * one place decides it.
         */
        val ORDER: Comparator<ClockStamp> = Comparator { left, right ->
            when (left.clock.relate(right.clock)) {
                VectorClock.Relation.BEFORE -> -1
                VectorClock.Relation.AFTER -> 1
                VectorClock.Relation.EQUAL -> left.origin.value.compareTo(right.origin.value)
                VectorClock.Relation.CONCURRENT -> left.origin.value.compareTo(right.origin.value)
            }
        }
    }
}
