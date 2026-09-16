package com.demo.chat.index.cassandra.impl

import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyValuePair
import com.demo.chat.index.cassandra.domain.ChatKeyValueIndex
import com.demo.chat.index.cassandra.domain.ChatKeyValueIndexById
import com.demo.chat.index.cassandra.domain.ChatKeyValueIndexByIdKey
import com.demo.chat.index.cassandra.domain.ChatKeyValueIndexKey
import com.demo.chat.index.cassandra.repository.KeyValueIndexByIdRepository
import com.demo.chat.index.cassandra.repository.KeyValueIndexRepository
import com.demo.chat.service.core.KeyValueIndexFields
import com.demo.chat.service.core.KeyValueIndexService
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * An inverted index over key-value pair values.
 *
 * [KeyValueIndexFields] names the fields of one value type, so the lucene
 * index and this index store the same fields for one value.
 *
 * A write adds one row per field to each table. The tables carry the same
 * rows under different partitions, so both stay in step.
 *
 * A repeated add of one value writes the same primary keys. Cassandra
 * overwrites them, so the index holds one row per field per entity.
 */
class KeyValueIndex<T>(
    private val fields: KeyValueIndexFields,
    private val byFieldRepo: KeyValueIndexRepository<T>,
    private val byIdRepo: KeyValueIndexByIdRepository<T>,
) : KeyValueIndexService<T, Map<String, String>> {

    /**
     * Replaces every row of this entity.
     *
     * An insert alone would leave the rows of an earlier value, because a
     * changed value writes a different primary key. A job that reached
     * SUCCEEDED would still answer a query for RUNNING.
     *
     * The removal and the insert are two steps. A reader between them sees
     * no row for this entity.
     *
     * The field lookup runs at subscribe time and runs before the removal.
     * An unregistered value type then fails without destroying the rows that
     * the index already holds.
     */
    override fun add(entity: KeyValuePair<T, Any>): Mono<Void> =
        Mono.fromCallable { fields.fieldsOf(entity.data) }
            .flatMap { rows ->
                rem(entity.key)
                    .thenMany(
                        Flux.fromIterable(rows).concatMap { (field, value) ->
                            byFieldRepo
                                .save(ChatKeyValueIndex(ChatKeyValueIndexKey(field, value, entity.key.id)))
                                .then(
                                    byIdRepo.save(
                                        ChatKeyValueIndexById(
                                            ChatKeyValueIndexByIdKey(entity.key.id, field, value)
                                        )
                                    )
                                )
                        }
                    )
                    .then()
            }

    override fun rem(key: Key<T>): Mono<Void> =
        byIdRepo
            .findByKeyId(key.id)
            .concatMap { row ->
                byFieldRepo
                    .delete(ChatKeyValueIndex(ChatKeyValueIndexKey(row.key.field, row.key.value, key.id)))
                    .then(byIdRepo.delete(row))
            }
            .then()

    override fun findBy(query: Map<String, String>): Flux<out Key<T>> {
        val field = query.keys.firstOrNull() ?: return Flux.empty()
        val value = query[field] ?: return Flux.empty()

        return byFieldRepo
            .findByKeyFieldAndKeyValue(field, value)
            .map { row -> Key.funKey(row.key.id) }
    }

    override fun findUnique(query: Map<String, String>): Mono<out Key<T>> =
        findBy(query).singleOrEmpty()
}
