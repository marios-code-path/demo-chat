package com.demo.chat.service.composite.command.memory

import com.demo.chat.domain.command.AcceptedCommand
import com.demo.chat.domain.command.BackendId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

class DispatchEntry<T, V>(val command: AcceptedCommand<T, V>, val marker: CommitMarker) {
    /** The obligations that a runtime queue holds. A rescan skips them. */
    val dispatched: MutableSet<BackendId> = ConcurrentHashMap.newKeySet()
}

/** Dispatch entries in staging order. An entry stays hidden until its marker commits. */
class DispatchLog<T, V> {
    private val entries = ConcurrentLinkedQueue<DispatchEntry<T, V>>()

    fun stage(entry: DispatchEntry<T, V>) {
        entries.add(entry)
    }

    fun remove(entry: DispatchEntry<T, V>) {
        entries.remove(entry)
    }

    fun iterator(): MutableIterator<DispatchEntry<T, V>> = entries.iterator()

    fun size(): Int = entries.size
}
