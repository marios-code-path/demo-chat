package com.demo.chat.test.index.lucene.storage

import com.demo.chat.index.lucene.storage.FileStorage
import com.demo.chat.index.lucene.storage.LuceneStorages
import com.demo.chat.index.lucene.storage.MemoryStorage
import org.apache.lucene.store.ByteBuffersDirectory
import org.apache.lucene.store.FSDirectory
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class LuceneStorageTests {

    @Test
    fun `no root selects memory`() {
        val storage = LuceneStorages.of(null, null, null)
        assertThat(storage).isInstanceOf(MemoryStorage::class.java)
        assertThat(storage.summary).isEqualTo("memory")
        assertThat(storage.open("user")).isInstanceOf(ByteBuffersDirectory::class.java)
        assertThat(storage.requests("user")).isNull()
    }

    @Test
    fun `a blank root fails and names the property`() {
        assertThatThrownBy { LuceneStorages.of("  ", "long", 1) }.hasMessageContaining("app.index.lucene.root")
    }

    @Test
    fun `a root needs the key type and the node id`() {
        assertThatThrownBy { LuceneStorages.of("/tmp/x", null, 1) }.hasMessageContaining("app.key.type")
        assertThatThrownBy { LuceneStorages.of("/tmp/x", "long", null) }.hasMessageContaining("app.nodeid")
    }

    @Test
    fun `the path separates the key type and the node id`(@TempDir root: Path) {
        val one = LuceneStorages.of(root.toString(), "long", 1)
        val two = LuceneStorages.of(root.toString(), "long", 2)
        assertThat(one).isInstanceOf(FileStorage::class.java)
        assertThat(one.path("user")).isEqualTo(root.resolve("long").resolve("1").resolve("user"))
        assertThat(two.path("user")).isEqualTo(root.resolve("long").resolve("2").resolve("user"))
        assertThat(one.summary).isEqualTo("files at $root")
        one.open("user").use { assertThat(it).isInstanceOf(FSDirectory::class.java) }
        assertThat(one.path("user")!!.toFile().isDirectory).isTrue()
        one.sync("user")
    }

    @Test
    fun `memory mode has nothing to sync`() {
        MemoryStorage().sync("user")
    }
}
