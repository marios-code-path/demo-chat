package com.demo.chat.index.lucene.impl

import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyValuePair
import com.demo.chat.domain.TypeUtil
import com.demo.chat.index.lucene.domain.IndexEntryEncoder
import com.demo.chat.service.core.KeyValueIndexService
import reactor.core.publisher.Mono

/**
 * A key-value entry is mutable, so this index replaces the entry of a key.
 *
 * The base index adds a document per call. A second add under one key would
 * leave the document of the earlier value in place, so a query for that
 * value would still answer, and findUnique would fail once two documents
 * matched. A job that reached SUCCEEDED would stay searchable as RUNNING.
 */
open class KeyValueLuceneIndex<T>(
    typeUtil: TypeUtil<T>,
    private val entryEncoder: IndexEntryEncoder<KeyValuePair<T, Any>>
) : KeyValueIndexService<T, IndexSearchRequest>,
    LuceneIndex<T, KeyValuePair<T, Any>>(
        entryEncoder,
        keyEncoder = { str -> Key.funKey(typeUtil.fromString(str)) },
        keyReceiver = { t -> t.key }
    ) {

    // The fields are read before the removal. An unregistered value type then
    // fails without destroying the entry that the index already holds.
    override fun add(entity: KeyValuePair<T, Any>): Mono<Void> =
        Mono.fromCallable { entryEncoder.apply(entity) }
            .flatMap { rem(entity.key).then(super.add(entity)) }
}
