package com.demo.chat.test.index.lucene

import com.demo.chat.domain.Key
import com.demo.chat.index.lucene.LuceneIndexLoad
import com.demo.chat.index.lucene.impl.IndexState
import com.demo.chat.index.lucene.impl.LuceneIndex
import com.demo.chat.index.lucene.storage.FileStorage
import com.demo.chat.index.lucene.storage.MemoryStorage
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/** Plan decision 1: the no-store behaviour of each mode. */
class LuceneIndexLoadTests {

    private fun index() = LuceneIndex<Long, String>({ listOf("v" to it) }, { Key.of(it.toLong(), -9L) }, { Key.of(it.length.toLong(), -9L) })

    @Test
    fun `memory mode with no store opens empty`() {
        val index = index()
        LuceneIndexLoad(index, "user", MemoryStorage(), null).load().block()
        assertThat(index.state()).isEqualTo(IndexState.OPEN)
        assertThat(index.liveDocuments()).isEqualTo(0)
        index.close()
    }

    @Test
    fun `files mode with no store fails and touches no file`(@TempDir root: Path) {
        val index = index()
        assertThatThrownBy { LuceneIndexLoad(index, "user", FileStorage(root, "long", 1), null).load().block() }
            .hasStackTraceContaining("no local store exists")
        assertThat(index.state()).isEqualTo(IndexState.FAILED)
        assertThat(Files.exists(root.resolve("long"))).isFalse()
    }

    /**
     * Spring Framework 7 stops a cached test context and starts it again on
     * reuse, so RootKeyStartup runs every load a second time. The index objects
     * are the same, and they stayed current while the process ran.
     */
    @Test
    fun `a second load of an open index changes nothing`() {
        val index = index()
        val load = LuceneIndexLoad(index, "user", MemoryStorage(), null)
        load.load().block()
        index.add("abc").block()
        load.load().block()
        assertThat(index.state()).isEqualTo(IndexState.OPEN)
        assertThat(index.liveDocuments()).isEqualTo(1)
        index.close()
    }

    @Test
    fun `a second load of a failed index fails again`(@TempDir root: Path) {
        val index = index()
        val load = LuceneIndexLoad(index, "user", FileStorage(root, "long", 1), null)
        assertThatThrownBy { load.load().block() }.hasStackTraceContaining("no local store exists")
        assertThatThrownBy { load.load().block() }.hasStackTraceContaining("Lucene index user failed")
    }
}
