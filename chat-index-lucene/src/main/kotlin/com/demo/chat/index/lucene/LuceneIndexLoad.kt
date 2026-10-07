package com.demo.chat.index.lucene

import com.demo.chat.index.lucene.impl.IndexState
import com.demo.chat.index.lucene.impl.LuceneIndex
import com.demo.chat.index.lucene.storage.LuceneStorage
import com.demo.chat.service.core.PersistenceStore
import com.demo.chat.service.core.StartupIndexLoad
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono

/**
 * Opens one Lucene index at start, against its store. See CHAT-ybtirmgj.
 *
 * With no store, memory mode opens the index empty, and files mode fails the
 * start, because no store can check the files.
 */
class LuceneIndexLoad<T, E : Any>(
    private val index: LuceneIndex<T, E>,
    private val name: String,
    private val storage: LuceneStorage,
    private val store: PersistenceStore<T, E>?,
) : StartupIndexLoad {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * A context can start again after a stop. Spring Framework 7 does that to a
     * cached test context, and RootKeyStartup then runs every load again. The
     * index objects are the same, and they stayed current while the process
     * ran. So a second load of an OPEN index changes nothing. A FAILED or
     * CLOSED index cannot open again, so its load fails the start.
     */
    override fun load(): Mono<Void> = Mono.fromRunnable {
        when (index.state()) {
            IndexState.NEW -> {
                if (store == null) {
                    logger.warn("lucene index $name: no local store exists. Memory mode opens the index empty.")
                }
                index.open(name, storage, store?.let { s -> { s.all() } })
            }
            IndexState.OPEN -> logger.debug("lucene index $name: already open. A restarted context reuses it.")
            IndexState.FAILED, IndexState.CLOSED ->
                throw IllegalStateException("Lucene index $name failed or closed. Its state is ${index.state()}.")
        }
    }
}
