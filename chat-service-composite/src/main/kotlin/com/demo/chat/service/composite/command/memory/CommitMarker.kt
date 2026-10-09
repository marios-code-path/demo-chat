package com.demo.chat.service.composite.command.memory

/**
 * The one admission commit marker of decision 7. The mapping, the dispatch
 * entry, and the status record refer to one marker. They stay hidden until
 * [commit], which is one volatile write that cannot fail.
 */
class CommitMarker {
    enum class State { UNCOMMITTED, COMMITTED, ROLLED_BACK }

    @Volatile
    var state: State = State.UNCOMMITTED
        private set

    val committed: Boolean get() = state == State.COMMITTED

    fun commit() {
        state = State.COMMITTED
    }

    fun rollBack() {
        state = State.ROLLED_BACK
    }
}
