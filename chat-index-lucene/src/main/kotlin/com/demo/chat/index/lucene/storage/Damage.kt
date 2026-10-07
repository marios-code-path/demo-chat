package com.demo.chat.index.lucene.storage

import org.apache.lucene.index.CorruptIndexException
import org.apache.lucene.index.IndexFormatTooNewException
import org.apache.lucene.index.IndexFormatTooOldException
import java.io.EOFException
import java.nio.file.NoSuchFileException

/**
 * The exceptions that mean damaged index files. A start recovers from them.
 * Every other exception fails the start, for example a refused permission or
 * a full disk. See the spec section "The damage class".
 */
object Damage {
    fun isDamage(e: Throwable): Boolean =
        e is CorruptIndexException ||
            e is IndexFormatTooOldException ||
            e is IndexFormatTooNewException ||
            e is EOFException ||
            e is NoSuchFileException
}
