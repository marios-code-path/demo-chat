package com.demo.chat.test.index.lucene.storage

import com.demo.chat.index.lucene.storage.syncDirectory
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.ReadableByteChannel
import java.nio.channels.WritableByteChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class DirectorySyncTests {

    @Test
    fun `a real directory syncs`(@TempDir dir: Path) {
        assertThatCode { syncDirectory(dir) }.doesNotThrowAnyException()
    }

    @Test
    fun `an open failure reaches the caller`(@TempDir dir: Path) {
        assertThatThrownBy { syncDirectory(dir.resolve("absent")) }.isInstanceOf(IOException::class.java)
    }

    /** The channel opens normally, and force fails. The real helper must report the failure. */
    @Test
    fun `a force failure reaches the caller`(@TempDir dir: Path) {
        val opener = { path: Path -> ForceFailingChannel(FileChannel.open(path, StandardOpenOption.READ)) }
        assertThatThrownBy { syncDirectory(dir, opener) }
            .isInstanceOf(IOException::class.java)
            .hasMessage("injected force failure")
    }
}

/** A channel that delegates every call except force, which fails. */
class ForceFailingChannel(private val delegate: FileChannel) : FileChannel() {
    override fun force(metaData: Boolean): Unit = throw IOException("injected force failure")
    override fun read(dst: ByteBuffer): Int = delegate.read(dst)
    override fun read(dsts: Array<out ByteBuffer>, offset: Int, length: Int): Long = delegate.read(dsts, offset, length)
    override fun write(src: ByteBuffer): Int = delegate.write(src)
    override fun write(srcs: Array<out ByteBuffer>, offset: Int, length: Int): Long = delegate.write(srcs, offset, length)
    override fun position(): Long = delegate.position()
    override fun position(newPosition: Long): FileChannel = apply { delegate.position(newPosition) }
    override fun size(): Long = delegate.size()
    override fun truncate(size: Long): FileChannel = apply { delegate.truncate(size) }
    override fun transferTo(position: Long, count: Long, target: WritableByteChannel): Long = delegate.transferTo(position, count, target)
    override fun transferFrom(src: ReadableByteChannel, position: Long, count: Long): Long = delegate.transferFrom(src, position, count)
    override fun read(dst: ByteBuffer, position: Long): Int = delegate.read(dst, position)
    override fun write(src: ByteBuffer, position: Long): Int = delegate.write(src, position)
    override fun map(mode: MapMode, position: Long, size: Long): MappedByteBuffer = delegate.map(mode, position, size)
    override fun lock(position: Long, size: Long, shared: Boolean): FileLock = delegate.lock(position, size, shared)
    override fun tryLock(position: Long, size: Long, shared: Boolean): FileLock? = delegate.tryLock(position, size, shared)
    override fun implCloseChannel() = delegate.close()
}
