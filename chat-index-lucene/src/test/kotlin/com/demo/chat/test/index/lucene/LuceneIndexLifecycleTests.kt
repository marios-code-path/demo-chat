package com.demo.chat.test.index.lucene

import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.Key
import com.demo.chat.index.lucene.impl.IndexState
import com.demo.chat.index.lucene.impl.LuceneIndex
import com.demo.chat.index.lucene.storage.FileStorage
import com.demo.chat.index.lucene.storage.MemoryStorage
import com.demo.chat.index.lucene.storage.StartKind
import org.apache.lucene.store.Directory
import org.apache.lucene.store.FilterDirectory
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import reactor.core.publisher.Flux
import reactor.core.scheduler.Schedulers
import java.time.Duration
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The lifecycle of one index. `beforeCommit` is internal, and a test source set
 * of the same Maven module can set it.
 */
class LuceneIndexLifecycleTests {

    data class Doc(val id: Long, val name: String)

    private companion object {
        /** A fixed root. These tests read no root. */
        const val ROOT = -9L
    }

    private fun index(encoder: (Doc) -> List<Pair<String, String>> = { listOf("name" to it.name) }) =
        LuceneIndex<Long, Doc>(encoder, { s -> Key.of(s.toLong(), ROOT) }, { d -> Key.of(d.id, ROOT) })

    private fun names(index: LuceneIndex<Long, Doc>, q: String) =
        index.findBy(IndexSearchRequest("name", q, 10)).map { it.id }.collectList().block()!!

    @Test
    fun `an operation before open fails`() {
        val index = index()
        assertThatThrownBy { index.add(Doc(1, "a")).block() }.hasMessageContaining("is not open")
        assertThatThrownBy { index.findBy(IndexSearchRequest("name", "a", 1)).blockFirst() }.hasMessageContaining("is not open")
    }

    @Test
    fun `an operation after close fails, and a second close does not throw`() {
        val index = index().apply { openInMemory("doc") }
        index.close()
        assertThatThrownBy { index.add(Doc(1, "a")).block() }.hasMessageContaining("Lucene index doc is closed")
        assertThatCode { index.close() }.doesNotThrowAnyException()
    }

    @Test
    fun `close is safe before open`() {
        assertThatCode { index().close() }.doesNotThrowAnyException()
    }

    @Test
    fun `two adds of one key give one hit with no old fields`() {
        val index = index().apply { openInMemory() }
        index.add(Doc(1, "old")).block()
        index.add(Doc(1, "new")).block()
        assertThat(names(index, "new")).containsExactly(1L)
        assertThat(names(index, "old")).isEmpty()
        assertThat(index.liveDocuments()).isEqualTo(1)
    }

    /** Pause after the writer change and before the commit. A query answers from the previous commit. */
    @Test
    fun `a query during a paused mutation answers from the previous commit`() {
        val index = index().apply { openInMemory() }
        val reached = CountDownLatch(1)
        val release = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(3)
        try {
            index.add(Doc(1, "before")).block()
            index.beforeCommit = { reached.countDown(); release.await(5, TimeUnit.SECONDS) }
            val write = pool.submit { index.add(Doc(1, "after")).block() }
            assertThat(reached.await(5, TimeUnit.SECONDS)).isTrue()

            // Each query runs on its own thread under a deadline. A query that waits for the mutation fails here.
            val before = pool.submit<List<Long>> { names(index, "before") }
            val after = pool.submit<List<Long>> { names(index, "after") }
            assertThat(before.get(5, TimeUnit.SECONDS)).containsExactly(1L)
            assertThat(after.get(5, TimeUnit.SECONDS)).isEmpty()

            release.countDown()
            write.get(5, TimeUnit.SECONDS)
            assertThat(names(index, "after")).containsExactly(1L)
        } finally {
            release.countDown()
            index.beforeCommit = {}
            pool.shutdownNow()
            index.close()
        }
    }

    @Test
    fun `a commit failure moves the index to FAILED and commits nothing`(@TempDir root: Path) {
        var failRename = false
        val storage = object : FileStorage(root, "long", 1) {
            override fun open(name: String): Directory = object : FilterDirectory(super.open(name)) {
                override fun rename(source: String, dest: String) {
                    if (failRename) throw IOException("injected rename failure")
                    super.rename(source, dest)
                }
            }
        }
        val index = index().apply { open("doc", storage) { Flux.empty() } }
        failRename = true
        assertThatThrownBy { index.add(Doc(1, "a")).block() }.hasStackTraceContaining("injected rename failure")
        assertThat(index.state()).isEqualTo(IndexState.FAILED)
        assertThatThrownBy { index.add(Doc(2, "b")).block() }.hasMessageContaining("Lucene index doc failed")
        index.close()

        val reopened = index().apply { open("doc", FileStorage(root, "long", 1)) { Flux.empty() } }
        assertThat(reopened.startOutcome()!!.kind).isEqualTo(StartKind.REUSED)
        assertThat(reopened.liveDocuments()).isEqualTo(0)
        reopened.close()
    }

    @Test
    fun `an encoder failure keeps the index OPEN`() {
        val index = index { throw IllegalArgumentException("injected encoder failure") }.apply { openInMemory() }
        assertThatThrownBy { index.add(Doc(1, "a")).block() }.hasMessageContaining("injected encoder failure")
        assertThat(index.state()).isEqualTo(IndexState.OPEN)
    }

    /** Review focus 5. */
    @Test
    fun `a query with bad syntax fails and the index stays OPEN`() {
        val index = index().apply { openInMemory() }
        assertThatThrownBy { index.findBy(IndexSearchRequest("name", "(unclosed", 10)).blockFirst() }
        assertThat(index.state()).isEqualTo(IndexState.OPEN)
    }

    @Test
    fun `a reopen after close reuses the files`(@TempDir root: Path) {
        val docs = listOf(Doc(1, "a"), Doc(2, "b"))
        val first = index().apply { open("doc", FileStorage(root, "long", 1)) { Flux.fromIterable(docs) } }
        first.close()
        val second = index().apply { open("doc", FileStorage(root, "long", 1)) { Flux.fromIterable(docs) } }
        assertThat(second.startOutcome()!!.kind).isEqualTo(StartKind.REUSED)
        assertThat(second.startOutcome()!!.documentsWritten).isEqualTo(0)
        assertThat(names(second, "b")).containsExactly(2L)
        second.close()
    }

    @Test
    fun `a query during the start fails`() {
        val index = index()
        val inStore = CountDownLatch(1)
        val release = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val opening = pool.submit {
                index.open("doc", MemoryStorage()) {
                    Flux.defer { inStore.countDown(); release.await(5, TimeUnit.SECONDS); Flux.just(Doc(1, "a")) }
                }
            }
            assertThat(inStore.await(5, TimeUnit.SECONDS)).isTrue()
            val query = pool.submit<Throwable?> { runCatching { names(index, "a") }.exceptionOrNull() }
            assertThat(query.get(5, TimeUnit.SECONDS)).hasMessageContaining("is not open")
            release.countDown()
            opening.get(5, TimeUnit.SECONDS)
            assertThat(names(index, "a")).containsExactly(1L)
        } finally {
            // Release first, so the open ends and close can take the lock.
            release.countDown()
            pool.shutdownNow()
            index.close()
        }
    }

    @Test
    fun `an encoder failure on an index that is not open reports the state`() {
        val index = index { throw IllegalArgumentException("injected encoder failure") }
        assertThatThrownBy { index.add(Doc(1, "a")).block() }.hasMessageContaining("is not open")
    }

    /**
     * A file commit syncs to disk. A caller can subscribe on a Netty event
     * loop, which is a non-blocking thread, so the commit must move off it.
     * Review of 9413ae55, CHAT-jknyeowy.
     */
    @Test
    fun `a file mutation never commits on a non-blocking thread`(@TempDir root: Path) {
        val index = index().apply { open("doc", FileStorage(root, "long", 1)) { Flux.empty() } }
        val onNonBlocking = mutableListOf<Boolean>()
        try {
            index.beforeCommit = { onNonBlocking += Schedulers.isInNonBlockingThread() }
            index.add(Doc(1, "a")).subscribeOn(Schedulers.parallel()).block(Duration.ofSeconds(5))
            index.rem(Key.of(1L, ROOT)).subscribeOn(Schedulers.parallel()).block(Duration.ofSeconds(5))
            assertThat(onNonBlocking).containsExactly(false, false)
            assertThat(index.liveDocuments()).isEqualTo(0)
        } finally {
            index.beforeCommit = {}
            index.close()
        }
    }
}
