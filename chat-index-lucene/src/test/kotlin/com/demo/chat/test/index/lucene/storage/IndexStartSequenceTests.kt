package com.demo.chat.test.index.lucene.storage

import com.demo.chat.index.lucene.storage.BuildReason
import com.demo.chat.index.lucene.storage.FileStorage
import com.demo.chat.index.lucene.storage.IndexHeader
import com.demo.chat.index.lucene.storage.IndexRequest
import com.demo.chat.index.lucene.storage.IndexRequests
import com.demo.chat.index.lucene.storage.MemoryStorage
import com.demo.chat.index.lucene.storage.OWNER_LOCK
import com.demo.chat.index.lucene.storage.StartKind
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.store.Directory
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.store.FilterDirectory
import org.apache.lucene.store.Lock
import org.apache.lucene.store.NativeFSLockFactory
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path

class IndexStartSequenceTests {

    private val a = Row("1", "alpha")
    private val b = Row("2", "beta")

    private fun files(root: Path, node: Int = 1) = FileStorage(root, "long", node)
    private fun dir(root: Path, node: Int = 1) = root.resolve("long").resolve(node.toString()).resolve("user")
    private fun names(root: Path) = Files.list(dir(root)).map { it.fileName.toString() }.toList().toSet()

    /** Builds the first commit, then closes. */
    private fun seed(root: Path, vararg rows: Row) = start(files(root), RowStore(rows.toList())).closeAll()

    @Test
    fun `memory mode builds from the store`() {
        val started = start(MemoryStorage(), RowStore(listOf(a, b)))
        assertThat(started.outcome.kind).isEqualTo(StartKind.BUILT)
        assertThat(started.outcome.reason).isEqualTo(BuildReason.NO_COMMIT)
        assertThat(started.outcome.documentsWritten).isEqualTo(2)
        started.closeAll()
    }

    @Test
    fun `memory mode with no store opens empty`() {
        val started = start(MemoryStorage(), null)
        assertThat(started.outcome.reason).isEqualTo(BuildReason.NO_COMMIT)
        assertThat(started.outcome.documentsWritten).isEqualTo(0)
        started.closeAll()
    }

    @Test
    fun `files mode with no store fails before any file access`(@TempDir root: Path) {
        assertThatThrownBy { start(files(root), null) }.hasMessageContaining("app.index.lucene.root")
        assertThat(Files.exists(root.resolve("long"))).isFalse()
    }

    @Test
    fun `intact files are reused and nothing is written`(@TempDir root: Path) {
        seed(root, a, b)
        val started = start(files(root), RowStore(listOf(a, b)))
        assertThat(started.outcome.kind).isEqualTo(StartKind.REUSED)
        assertThat(started.outcome.reason).isNull()
        assertThat(started.outcome.entitiesCompared).isEqualTo(2)
        assertThat(started.outcome.documentsWritten).isEqualTo(0)
        assertThat(started.holds("1")).isTrue()
        started.closeAll()
    }

    @Test
    fun `missing files build`(@TempDir root: Path) {
        seed(root, a)
        dir(root).toFile().deleteRecursively()
        val started = start(files(root), RowStore(listOf(a)))
        assertThat(started.outcome.reason).isEqualTo(BuildReason.NO_COMMIT)
        assertThat(started.holds("1")).isTrue()
        started.closeAll()
    }

    @Test
    fun `damaged files recover and build`(@TempDir root: Path) {
        seed(root, a, b)
        val segment = Files.list(dir(root)).filter { path ->
            val n = path.fileName.toString()
            n.startsWith("_") && !n.endsWith(".si")
        }.max(compareBy { Files.size(it) }).get()
        RandomAccessFile(segment.toFile(), "rw").use { file ->
            file.seek(file.length() / 2)
            file.write(ByteArray(16) { 0x5A })
        }
        val started = start(files(root), RowStore(listOf(a, b)))
        assertThat(started.outcome.reason).isEqualTo(BuildReason.DAMAGE)
        assertThat(started.holds("1")).isTrue()
        assertThat(started.holds("2")).isTrue()
        started.closeAll()
    }

    /** A store write with no index write, which a crash between the two leaves. */
    @Test
    fun `different content builds`(@TempDir root: Path) {
        seed(root, a)
        val started = start(files(root), RowStore(listOf(a, b)))
        assertThat(started.outcome.reason).isEqualTo(BuildReason.MISMATCH)
        assertThat(started.holds("2")).isTrue()
        started.closeAll()
    }

    @Test
    fun `a changed field builds`(@TempDir root: Path) {
        seed(root, a)
        val started = start(files(root), RowStore(listOf(a.copy(name = "changed"))))
        assertThat(started.outcome.reason).isEqualTo(BuildReason.MISMATCH)
        started.closeAll()
    }

    /** Review focus 2: a flushed store must empty the index. */
    @Test
    fun `an empty store empties the index`(@TempDir root: Path) {
        seed(root, a, b)
        val started = start(files(root), RowStore(emptyList()))
        assertThat(started.outcome.reason).isEqualTo(BuildReason.MISMATCH)
        assertThat(started.holds("1")).isFalse()
        started.closeAll()
    }

    /** Review focus 3: a UUID key text holds hyphens. */
    @Test
    fun `uuid keys are reused`(@TempDir root: Path) {
        val u1 = Row("550e8400-e29b-41d4-a716-446655440000", "one")
        val u2 = Row("550e8400-0000-0000-0000-000000000000", "two")
        seed(root, u1, u2)
        val started = start(files(root), RowStore(listOf(u1, u2)))
        assertThat(started.outcome.kind).isEqualTo(StartKind.REUSED)
        started.closeAll()
    }

    @Test
    fun `a header change builds`(@TempDir root: Path) {
        FSDirectory.open(dir(root).also { Files.createDirectories(it) }, NativeFSLockFactory.INSTANCE).use { d ->
            IndexWriter(d, IndexWriterConfig(StandardAnalyzer())).use { w ->
                val header = IndexHeader.current("user", "long", "1", StandardAnalyzer()).copy(format = "0")
                w.setLiveCommitData(header.userData().entries)
                w.commit()
            }
        }
        val started = start(files(root), RowStore(listOf(a)))
        assertThat(started.outcome.reason).isEqualTo(BuildReason.HEADER)
        started.closeAll()
    }

    @Test
    fun `a repeated key in the compare scan fails and deletes nothing`(@TempDir root: Path) {
        seed(root, a, b)
        val before = names(root)
        assertThatThrownBy { start(files(root), RowStore(listOf(a, a))) }.hasMessageContaining("more than once")
        assertThat(names(root)).isEqualTo(before)
    }

    @Test
    fun `a repeated key in the build scan fails before any commit`(@TempDir root: Path) {
        assertThatThrownBy { start(files(root), RowStore(listOf(a, a))) }.hasMessageContaining("more than once")
        assertThat(names(root).none { it.startsWith("segments_") }).isTrue()
    }

    @Test
    fun `a store error in the compare keeps the previous commit`(@TempDir root: Path) {
        seed(root, a)
        assertThatThrownBy { start(files(root), RowStore(listOf(a), failOnScan = 1)) }
            .hasStackTraceContaining("injected store failure")
        val started = start(files(root), RowStore(listOf(a)))
        assertThat(started.outcome.kind).isEqualTo(StartKind.REUSED)
        started.closeAll()
    }

    /** The compare scan finds a mismatch and succeeds. The build scan fails after one writer update. */
    @Test
    fun `a failed build on the mismatch path keeps the previous commit`(@TempDir root: Path) {
        seed(root, a)
        val store = RowStore(listOf(a, b))
        val encoder = FailingEncoder(store, failKey = "2", onScan = 2)
        assertThatThrownBy { start(files(root), store, entry = encoder::entryOf) }
            .hasMessageContaining("injected encoder failure")
        assertThat(store.scans).isEqualTo(2)
        assertThat(encoder.encodedInFailingScan).isEqualTo(1)
        val started = start(files(root), RowStore(listOf(a)))
        assertThat(started.outcome.kind).isEqualTo(StartKind.REUSED)
        started.closeAll()
    }

    /** A drop request skips the compare, so the build scan is the first scan. */
    @Test
    fun `a failed build on the recovery path leaves only lock and request files`(@TempDir root: Path) {
        seed(root, a)
        IndexRequests(dir(root)).write(IndexRequest.DROP)
        val store = RowStore(listOf(a, b))
        val encoder = FailingEncoder(store, failKey = "2", onScan = 1)
        assertThatThrownBy { start(files(root), store, entry = encoder::entryOf) }
            .hasMessageContaining("injected encoder failure")
        assertThat(encoder.encodedInFailingScan).isEqualTo(1)
        assertThat(names(root)).isSubsetOf("write.lock", OWNER_LOCK, "chat-drop.request", "chat-rebuild.request")
        assertThat(names(root)).contains("chat-drop.request")
        val started = start(files(root), RowStore(listOf(a)))
        assertThat(started.outcome.reason).isEqualTo(BuildReason.REQUESTED_DROP)
        assertThat(names(root)).doesNotContain("chat-drop.request")
        started.closeAll()
    }

    @Test
    fun `a rebuild request builds and is cleared`(@TempDir root: Path) {
        seed(root, a)
        IndexRequests(dir(root)).write(IndexRequest.REBUILD)
        val started = start(files(root), RowStore(listOf(a)))
        assertThat(started.outcome.reason).isEqualTo(BuildReason.REQUESTED_REBUILD)
        assertThat(names(root)).doesNotContain("chat-rebuild.request")
        started.closeAll()
    }

    @Test
    fun `a drop request clears both requests`(@TempDir root: Path) {
        seed(root, a)
        IndexRequests(dir(root)).write(IndexRequest.REBUILD)
        IndexRequests(dir(root)).write(IndexRequest.DROP)
        val started = start(files(root), RowStore(listOf(a)))
        assertThat(started.outcome.reason).isEqualTo(BuildReason.REQUESTED_DROP)
        assertThat(names(root)).doesNotContain("chat-rebuild.request", "chat-drop.request")
        started.closeAll()
    }

    @Test
    fun `two node ids use two directories`(@TempDir root: Path) {
        val one = start(files(root, 1), RowStore(listOf(a)))
        val two = start(files(root, 2), RowStore(listOf(b)))
        assertThat(dir(root, 1)).isNotEqualTo(dir(root, 2))
        assertThat(one.holds("1")).isTrue()
        assertThat(two.holds("2")).isTrue()
        one.closeAll()
        two.closeAll()
    }

    @Test
    fun `one node id twice fails on the owner lock and deletes nothing`(@TempDir root: Path) {
        val one = start(files(root), RowStore(listOf(a)))
        val before = names(root)
        assertThatThrownBy { start(files(root), RowStore(listOf(a))) }.hasMessageContaining("in use by another process")
        assertThat(names(root)).isEqualTo(before)
        one.closeAll()
    }

    @Test
    /**
     * IndexWriter takes write.lock before it reads a commit. So a damaged index
     * whose write.lock another holder keeps fails at the writer, before any
     * recovery step can delete a file.
     */
    fun `write lock held elsewhere fails the start and deletes nothing`(@TempDir root: Path) {
        seed(root, a)
        Files.list(dir(root)).filter { it.fileName.toString().startsWith("segments_") }
            .forEach { RandomAccessFile(it.toFile(), "rw").use { f -> f.setLength(4) } }
        val before = names(root)
        FSDirectory.open(dir(root), NativeFSLockFactory.INSTANCE).use { other ->
            other.obtainLock(IndexWriter.WRITE_LOCK_NAME).use {
                assertThatThrownBy { start(files(root), RowStore(listOf(a))) }.hasMessageContaining("write.lock")
            }
        }
        assertThat(names(root)).isEqualTo(before)
    }

    /** Review focus 1: a root that is a regular file fails the start, and it is not damage. */
    @Test
    fun `a root that is a file fails the start`(@TempDir tmp: Path) {
        val root = Files.writeString(tmp.resolve("root-file"), "x")
        assertThatThrownBy { start(files(root), RowStore(listOf(a))) }.isInstanceOf(IOException::class.java)
        assertThat(Files.readString(root)).isEqualTo("x")
    }

    @Test
    fun `a commit failure fails the start, and the next start checks again`(@TempDir root: Path) {
        val failing = object : FileStorage(root, "long", 1) {
            override fun open(name: String): Directory = object : FilterDirectory(super.open(name)) {
                override fun rename(source: String, dest: String) {
                    throw IOException("injected rename failure")
                }
            }
        }
        assertThatThrownBy { start(failing, RowStore(listOf(a))) }.hasMessageContaining("committed state is uncertain")
        val started = start(files(root), RowStore(listOf(a)))
        assertThat(started.holds("1")).isTrue()
        started.closeAll()
    }

    @Test
    fun `a failure after the commit fails the start, and the request remains`(@TempDir root: Path) {
        seed(root, a)
        IndexRequests(dir(root)).write(IndexRequest.REBUILD)
        val failing = object : FileStorage(root, "long", 1) {
            override fun requests(name: String) = IndexRequests(path(name), delete = { throw IOException("injected delete failure") })
        }
        assertThatThrownBy { start(failing, RowStore(listOf(a))) }.hasStackTraceContaining("injected delete failure")
        val started = start(files(root), RowStore(listOf(a)))
        assertThat(started.outcome.reason).isEqualTo(BuildReason.REQUESTED_REBUILD)
        started.closeAll()
    }

    @Test
    fun `request files survive a commit`(@TempDir root: Path) {
        seed(root, a)
        val requests = IndexRequests(dir(root))
        requests.write(IndexRequest.REBUILD)
        requests.write(IndexRequest.DROP)
        // The drop build clears both files. Write them again, then commit.
        val started = start(files(root), RowStore(listOf(a)))
        requests.write(IndexRequest.REBUILD)
        requests.write(IndexRequest.DROP)
        started.writer.updateDocument(org.apache.lucene.index.Term("_key", "9"), encode(Row("9", "x")).document)
        started.writer.commit()
        assertThat(names(root)).contains("chat-rebuild.request", "chat-drop.request")
        started.closeAll()
    }

    @Test
    fun `a lock failure that is not a held lock closes the directory`(@TempDir root: Path) {
        var closed = false
        val failing = object : FileStorage(root, "long", 1) {
            override fun open(name: String): Directory = object : FilterDirectory(super.open(name)) {
                override fun obtainLock(lockName: String): Lock = throw IOException("injected lock failure")
                override fun close() {
                    closed = true
                    super.close()
                }
            }
        }
        assertThatThrownBy { start(failing, RowStore(listOf(a))) }.hasMessage("injected lock failure")
        assertThat(closed).isTrue()
    }

    @Test
    fun `a rollback error stops recovery before any deletion`(@TempDir root: Path) {
        seed(root, a, b)
        IndexRequests(dir(root)).write(IndexRequest.DROP)
        val before = names(root)
        assertThatThrownBy {
            start(files(root), RowStore(listOf(a)), rollback = { throw IOException("injected rollback failure") })
        }.hasMessage("injected rollback failure")
        assertThat(names(root)).isEqualTo(before)
        val started = start(files(root), RowStore(listOf(a)))
        assertThat(started.outcome.reason).isEqualTo(BuildReason.REQUESTED_DROP)
        started.closeAll()
    }

    @Test
    fun `every build syncs the directory, with no request pending`(@TempDir root: Path) {
        var syncs = 0
        val counting = object : FileStorage(root, "long", 1) {
            override fun sync(name: String) {
                syncs++
                super.sync(name)
            }
        }
        start(counting, RowStore(listOf(a))).closeAll()
        assertThat(syncs).isEqualTo(1)
    }

    @Test
    fun `a sync failure after the build fails the start, and the next start checks again`(@TempDir root: Path) {
        val failing = object : FileStorage(root, "long", 1) {
            override fun sync(name: String) {
                throw IOException("injected sync failure")
            }
        }
        assertThatThrownBy { start(failing, RowStore(listOf(a))) }.hasMessage("injected sync failure")
        val started = start(files(root), RowStore(listOf(a)))
        assertThat(started.holds("1")).isTrue()
        started.closeAll()
    }
}
