package com.demo.chat.service.vector

import com.demo.chat.domain.JobRecord
import reactor.core.publisher.Mono

/**
 * Writes one progress record of a rebuild.
 *
 * An implementation must not reach the vector indexer. A job record in the
 * recall corpus would let a later rebuild read its own output back.
 */
fun interface JobRecordWriter<T> {
    fun write(record: JobRecord<T>): Mono<Void>
}
