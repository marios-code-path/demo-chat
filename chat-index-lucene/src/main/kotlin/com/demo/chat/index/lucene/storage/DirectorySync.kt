package com.demo.chat.index.lucene.storage

import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * Syncs the metadata of one directory, and reports every failure.
 *
 * Do not use `IOUtils.fsync(directory, true)` in its place. Lucene 8.7
 * suppresses an IOException from force on a directory, so that call cannot
 * prove that a request file is durable. The opener is a parameter, so a test
 * can pass a channel whose force fails and still run this body.
 */
fun syncDirectory(
    dir: Path,
    opener: (Path) -> FileChannel = { FileChannel.open(it, StandardOpenOption.READ) },
) {
    opener(dir).use { it.force(true) }
}
