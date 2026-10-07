package com.demo.chat.test.index.lucene

import com.demo.chat.domain.Key
import com.demo.chat.index.lucene.LuceneIndexRegistry
import com.demo.chat.index.lucene.impl.LuceneIndex
import com.demo.chat.index.lucene.storage.FileStorage
import com.demo.chat.index.lucene.storage.MemoryStorage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import reactor.core.publisher.Flux
import java.nio.file.Files
import java.nio.file.Path

class LuceneIndexRegistryTests {

    private fun index() = LuceneIndex<Long, String>({ listOf("v" to it) }, { Key.of(it.toLong(), -9L) }, { Key.of(it.length.toLong(), -9L) })

    @Test
    fun `memory mode refuses both commands`() {
        val registry = LuceneIndexRegistry(MemoryStorage(), mapOf("user" to index().apply { openInMemory("user") }))
        val rebuild = registry.requestRebuild("user")
        val drop = registry.requestDrop("user")
        assertThat(rebuild.accepted).isFalse()
        assertThat(drop.accepted).isFalse()
        assertThat(rebuild.reason).contains("in memory")
    }

    @Test
    fun `an unknown name lists the indexes`() {
        val registry = LuceneIndexRegistry(MemoryStorage(), mapOf("user" to index(), "topic" to index()))
        val result = registry.requestRebuild("nope")
        assertThat(result.accepted).isFalse()
        assertThat(result.reason).contains("user, topic")
    }

    @Test
    fun `files mode writes the request and reports it`(@TempDir root: Path) {
        val storage = FileStorage(root, "long", 1)
        val user = index().apply { open("user", storage) { Flux.just("abc") } }
        val registry = LuceneIndexRegistry(storage, mapOf("user" to user))
        assertThat(registry.requestDrop("user").accepted).isTrue()
        assertThat(Files.exists(root.resolve("long/1/user/chat-drop.request"))).isTrue()
        val report = registry.reports().single()
        assertThat(report.mode).isEqualTo("files")
        assertThat(report.state).isEqualTo("OPEN")
        assertThat(report.outcome).isEqualTo("BUILT")
        assertThat(report.reason).isEqualTo("NO_COMMIT")
        assertThat(report.liveDocuments).isEqualTo(1)
        assertThat(report.pendingRequest).isEqualTo("DROP")
        user.close()
    }
}
