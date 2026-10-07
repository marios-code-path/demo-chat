package com.demo.chat.index.lucene.storage

import com.demo.chat.service.core.IndexRequestResult
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/** An operator request. Neither name starts with "_" or "segments", so Lucene never deletes it. */
enum class IndexRequest(val fileName: String) {
    REBUILD("chat-rebuild.request"),
    DROP("chat-drop.request"),
}

/**
 * The request files of one index directory. Only the start sequence acts on a
 * request. See the spec section "Request durability".
 */
class IndexRequests(
    private val dir: Path,
    private val sync: (Path) -> Unit = { syncDirectory(it) },
    private val delete: (Path) -> Unit = { Files.deleteIfExists(it) },
) {

    /** A crashed command can leave a temporary file. It never counts as a request. */
    fun removeStaleTemporary() {
        IndexRequest.entries.forEach { Files.deleteIfExists(temporary(it)) }
    }

    /** A drop request takes precedence when both files exist. */
    fun pending(): IndexRequest? = when {
        Files.exists(dir.resolve(IndexRequest.DROP.fileName)) -> IndexRequest.DROP
        Files.exists(dir.resolve(IndexRequest.REBUILD.fileName)) -> IndexRequest.REBUILD
        else -> null
    }

    fun write(request: IndexRequest): IndexRequestResult {
        val temporary = temporary(request)
        val target = dir.resolve(request.fileName)
        try {
            FileChannel.open(
                temporary, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING,
            ).use { channel ->
                channel.write(ByteBuffer.wrap(request.name.toByteArray(Charsets.UTF_8)))
                channel.force(true)
            }
        } catch (e: IOException) {
            return refusedBeforeMove("The request file could not be written: ${e.message}.")
        }
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: AtomicMoveNotSupportedException) {
            Files.deleteIfExists(temporary)
            return refusedBeforeMove("The file system refused an atomic move: ${e.message}.")
        } catch (e: IOException) {
            Files.deleteIfExists(temporary)
            return refusedBeforeMove("The request file could not be moved into place: ${e.message}.")
        }
        try {
            sync(dir)
        } catch (e: IOException) {
            return IndexRequestResult(
                false,
                "The request file is in place, and the directory sync failed: ${e.message}. " +
                    "The request can remain pending, and the next start can act on it.",
                target.toString(),
            )
        }
        return IndexRequestResult(true, "The next start acts on this request.", target.toString())
    }

    /** Deletes the named requests, then syncs the directory. Any failure reaches the caller. */
    fun clear(requests: List<IndexRequest>) {
        if (requests.isEmpty()) return
        requests.forEach { delete(dir.resolve(it.fileName)) }
        sync(dir)
    }

    private fun temporary(request: IndexRequest): Path = dir.resolve(request.fileName + ".tmp")

    private fun refusedBeforeMove(cause: String) = IndexRequestResult(
        false, "$cause This attempt installs no new request. Any previously pending request remains.", null,
    )

    companion object {
        /** A drop supersedes a rebuild, so a drop build clears both. */
        fun clearedBy(pending: IndexRequest?): List<IndexRequest> = when (pending) {
            IndexRequest.DROP -> listOf(IndexRequest.DROP, IndexRequest.REBUILD)
            IndexRequest.REBUILD -> listOf(IndexRequest.REBUILD)
            null -> emptyList()
        }
    }
}
