package com.demo.chat.test.index.lucene.storage

import com.demo.chat.index.lucene.storage.IndexRequest
import com.demo.chat.index.lucene.storage.IndexRequests
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class IndexRequestsTests {

    @Test
    fun `a written request is pending and durable`(@TempDir dir: Path) {
        val result = IndexRequests(dir).write(IndexRequest.REBUILD)
        assertThat(result.accepted).isTrue()
        assertThat(result.path).isEqualTo(dir.resolve("chat-rebuild.request").toString())
        assertThat(IndexRequests(dir).pending()).isEqualTo(IndexRequest.REBUILD)
        assertThat(Files.list(dir).map { it.fileName.toString() }.toList()).containsExactly("chat-rebuild.request")
    }

    @Test
    fun `a drop request takes precedence`(@TempDir dir: Path) {
        val requests = IndexRequests(dir)
        requests.write(IndexRequest.REBUILD)
        requests.write(IndexRequest.DROP)
        assertThat(requests.pending()).isEqualTo(IndexRequest.DROP)
    }

    @Test
    fun `a stale temporary file is removed and never counts`(@TempDir dir: Path) {
        Files.writeString(dir.resolve("chat-drop.request.tmp"), "DROP")
        val requests = IndexRequests(dir)
        assertThat(requests.pending()).isNull()
        requests.removeStaleTemporary()
        assertThat(Files.exists(dir.resolve("chat-drop.request.tmp"))).isFalse()
    }

    /** The move succeeded, so the answer must say that the request can remain pending. */
    @Test
    fun `a directory sync failure after the move reports a pending request`(@TempDir dir: Path) {
        val requests = IndexRequests(dir, sync = { throw IOException("injected sync failure") })
        val result = requests.write(IndexRequest.DROP)
        assertThat(result.accepted).isFalse()
        assertThat(result.reason).contains("can remain pending").contains("injected sync failure")
        assertThat(result.path).isEqualTo(dir.resolve("chat-drop.request").toString())
        assertThat(Files.exists(dir.resolve("chat-drop.request"))).isTrue()
    }

    @Test
    fun `a failure before the move keeps an earlier request`(@TempDir dir: Path) {
        IndexRequests(dir).write(IndexRequest.REBUILD)
        Files.createDirectory(dir.resolve("chat-drop.request.tmp"))
        val result = IndexRequests(dir).write(IndexRequest.DROP)
        assertThat(result.accepted).isFalse()
        assertThat(result.reason).contains("This attempt installs no new request. Any previously pending request remains.")
        assertThat(IndexRequests(dir).pending()).isEqualTo(IndexRequest.REBUILD)
    }

    @Test
    fun `clear deletes the requests and syncs`(@TempDir dir: Path) {
        var synced = 0
        val requests = IndexRequests(dir, sync = { synced++ })
        requests.write(IndexRequest.REBUILD)
        requests.write(IndexRequest.DROP)
        requests.clear(IndexRequests.clearedBy(IndexRequest.DROP))
        assertThat(requests.pending()).isNull()
        assertThat(synced).isEqualTo(3)
    }

    @Test
    fun `a drop clears both, and a rebuild clears only itself`() {
        assertThat(IndexRequests.clearedBy(IndexRequest.DROP)).containsExactly(IndexRequest.DROP, IndexRequest.REBUILD)
        assertThat(IndexRequests.clearedBy(IndexRequest.REBUILD)).containsExactly(IndexRequest.REBUILD)
        assertThat(IndexRequests.clearedBy(null)).isEmpty()
    }

    @Test
    fun `a delete failure reaches the caller`(@TempDir dir: Path) {
        val requests = IndexRequests(dir, delete = { throw IOException("injected delete failure") })
        requests.write(IndexRequest.REBUILD)
        assertThatThrownBy { requests.clear(listOf(IndexRequest.REBUILD)) }.hasMessage("injected delete failure")
    }
}
