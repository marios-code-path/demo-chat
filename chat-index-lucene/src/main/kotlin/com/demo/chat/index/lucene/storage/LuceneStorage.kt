package com.demo.chat.index.lucene.storage

import com.demo.chat.domain.ChatException
import org.apache.lucene.store.ByteBuffersDirectory
import org.apache.lucene.store.Directory
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.store.NativeFSLockFactory
import java.nio.file.Files
import java.nio.file.Path

/** Where the Lucene indexes of one process live. See CHAT-ybtirmgj. */
interface LuceneStorage {
    val mode: String
    val persistent: Boolean
    val keyType: String
    val nodeId: String
    val summary: String
    fun open(name: String): Directory
    fun path(name: String): Path?
    fun requests(name: String): IndexRequests?

    /** Syncs the index directory. Memory mode has nothing to sync. Any failure reaches the caller. */
    fun sync(name: String)

    fun describe(name: String): String = path(name)?.toString() ?: "memory"
}

/** Each index lives in process memory, and each start builds it. */
class MemoryStorage : LuceneStorage {
    override val mode = "memory"
    override val persistent = false
    override val keyType = "-"
    override val nodeId = "-"
    override val summary = "memory"
    override fun open(name: String): Directory = ByteBuffersDirectory()
    override fun path(name: String): Path? = null
    override fun requests(name: String): IndexRequests? = null
    override fun sync(name: String) = Unit
}

/**
 * Each index lives in `<root>/<keyType>/<nodeId>/<index>`. Open so that a test
 * can replace the request files or the directory.
 */
open class FileStorage(private val root: Path, keyType: String, nodeId: Int) : LuceneStorage {
    override val mode = "files"
    override val persistent = true
    override val keyType = keyType
    override val nodeId = nodeId.toString()
    override val summary = "files at $root"

    override fun path(name: String): Path = root.resolve(keyType).resolve(nodeId.toString()).resolve(name)

    override fun open(name: String): Directory {
        val dir = Files.createDirectories(path(name))
        return FSDirectory.open(dir, NativeFSLockFactory.INSTANCE)
    }

    override fun requests(name: String): IndexRequests? = IndexRequests(path(name))

    override fun sync(name: String) = syncDirectory(path(name))
}

object LuceneStorages {
    const val ROOT = "app.index.lucene.root"

    fun of(root: String?, keyType: String?, nodeId: Int?): LuceneStorage {
        if (root == null) return MemoryStorage()
        if (root.isBlank()) {
            throw ChatException("$ROOT is blank. Remove it to keep the Lucene indexes in memory, or set a directory.")
        }
        val type = keyType ?: throw ChatException("$ROOT is set, and app.key.type is not. The index path needs both.")
        val node = nodeId ?: throw ChatException("$ROOT is set, and app.nodeid is not. The index path needs both.")
        return FileStorage(Path.of(root), type, node)
    }
}
