package com.demo.chat.service.core

import reactor.core.publisher.Mono

/**
 * One index load at start. It fills an in-process index from its store. The
 * start sequence runs every load after the roots load and before readiness,
 * and before any server accepts a request. Any error fails the start. See
 * `CHAT-bafkgkko`.
 */
fun interface StartupIndexLoad {
    fun load(): Mono<Void>
}

/**
 * This load adds every stored entity to the index, one at a time. The first
 * error ends the load with that error, so a partial load fails the start.
 */
class PersistedIndexLoad<T, E : Any>(
    private val persistence: PersistenceStore<T, E>,
    private val index: IndexService<T, E, *>,
) : StartupIndexLoad {
    override fun load(): Mono<Void> = persistence.all().concatMap { index.add(it) }.then()
}
