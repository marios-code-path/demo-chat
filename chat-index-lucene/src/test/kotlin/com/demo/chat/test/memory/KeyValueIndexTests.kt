package com.demo.chat.test.memory

import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyValuePair
import com.demo.chat.domain.LongUtil
import com.demo.chat.domain.UUIDUtil
import com.demo.chat.index.lucene.domain.IndexEntryEncoder
import com.demo.chat.index.lucene.impl.KeyValueLuceneIndex
import com.demo.chat.service.core.IndexService
import com.demo.chat.service.core.KeyValueIndexFields
import com.demo.chat.service.core.KeyValueIndexFieldsEntry
import com.demo.chat.service.core.TypedKeyValueIndexFields
import com.demo.chat.test.index.IndexTests
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import reactor.test.StepVerifier
import java.util.UUID
import java.util.function.Function
import java.util.function.Supplier

data class IndexedSample(val name: String, val size: Int)

private val sampleFields = TypedKeyValueIndexFields(
    listOf(
        KeyValueIndexFieldsEntry(
            IndexedSample::class.java,
            KeyValueIndexFields { value ->
                val sample = value as IndexedSample
                listOf(Pair("name", sample.name), Pair("size", sample.size.toString()))
            }
        )
    )
)

class KeyValueIndexTests : IndexTests<Long, KeyValuePair<Long, Any>, IndexSearchRequest>(
    KeyValueLuceneIndex(LongUtil(), IndexEntryEncoder.ofKeyValueFields(sampleFields)),
    Supplier { KeyValuePair.create(Key.funKey(1234L), IndexedSample("TEST", 7) as Any) },
    Function { pair -> pair.key },
    Supplier { IndexSearchRequest("name", "TEST", 1000) }
) {
    override fun getIndex(): IndexService<Long, KeyValuePair<Long, Any>, IndexSearchRequest> = myIndex

    /**
     * Overrides the shared base test. A key-value entry is mutable, so the
     * index replaces the entry of a key instead of adding a second document.
     * Two entries therefore need two keys.
     */
    @Test
    override fun `should save and find many`() {
        val index = getIndex()

        val composed = index.add(KeyValuePair.create(Key.funKey(4001L), IndexedSample("TEST", 1) as Any))
            .then(index.add(KeyValuePair.create(Key.funKey(4002L), IndexedSample("TEST", 2) as Any)))
            .thenMany(index.findBy(IndexSearchRequest("name", "TEST", 1000)))

        StepVerifier
            .create(composed)
            .expectNextCount(2)
            .verifyComplete()
    }

    // A stored value changes. The entry of the earlier value must not answer
    // a later query, or a finished job stays searchable as a running one.
    @Test
    fun `a changed value replaces the entry of the earlier value`() {
        val index = getIndex()
        val key = Key.funKey(5150L)

        StepVerifier
            .create(
                index.add(KeyValuePair.create(key, IndexedSample("RUNNING", 1) as Any))
                    .then(index.add(KeyValuePair.create(key, IndexedSample("SUCCEEDED", 1) as Any)))
                    .thenMany(index.findBy(IndexSearchRequest("name", "RUNNING", 10)))
            )
            .verifyComplete()

        StepVerifier
            .create(index.findBy(IndexSearchRequest("name", "SUCCEEDED", 10)))
            .assertNext { found -> Assertions.assertThat(found.id).isEqualTo(5150L) }
            .verifyComplete()
    }

    // Two uuid keys that share their first segment. The key field is
    // analyzed, and a hyphen is the QueryParser NOT operator, so a parsed
    // removal never matched one key exactly. Removing one entity deleted
    // both. The removal must use an exact term.
    @Test
    fun `a removal by a uuid key leaves an entity that shares a segment`() {
        val index = KeyValueLuceneIndex(UUIDUtil(), IndexEntryEncoder.ofKeyValueFields<UUID>(sampleFields))
        val first = Key.funKey(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"))
        val second = Key.funKey(UUID.fromString("550e8400-aaaa-bbbb-cccc-000000000001"))

        StepVerifier
            .create(
                index.add(KeyValuePair.create(first, IndexedSample("SHARED", 1) as Any))
                    .then(index.add(KeyValuePair.create(second, IndexedSample("SHARED", 2) as Any)))
                    .then(index.rem(first))
                    .thenMany(index.findBy(IndexSearchRequest("name", "SHARED", 10)))
            )
            .assertNext { found -> Assertions.assertThat(found.id).isEqualTo(second.id) }
            .verifyComplete()
    }

    // A replacement uses the same removal, so it must leave a key that shares
    // a segment alone.
    @Test
    fun `a replaced uuid entry leaves an entity that shares a segment`() {
        val index = KeyValueLuceneIndex(UUIDUtil(), IndexEntryEncoder.ofKeyValueFields<UUID>(sampleFields))
        val first = Key.funKey(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"))
        val second = Key.funKey(UUID.fromString("550e8400-aaaa-bbbb-cccc-000000000001"))

        StepVerifier
            .create(
                index.add(KeyValuePair.create(first, IndexedSample("SHARED", 1) as Any))
                    .then(index.add(KeyValuePair.create(second, IndexedSample("SHARED", 2) as Any)))
                    .then(index.add(KeyValuePair.create(first, IndexedSample("CHANGED", 1) as Any)))
                    .thenMany(index.findBy(IndexSearchRequest("name", "SHARED", 10)))
            )
            .assertNext { found -> Assertions.assertThat(found.id).isEqualTo(second.id) }
            .verifyComplete()
    }

    // The encoder runs once per add. A second call could fail after the
    // removal already ran, which would remove the entry and store nothing.
    @Test
    fun `an add reads the fields once`() {
        val calls = java.util.concurrent.atomic.AtomicInteger()
        val counting = IndexEntryEncoder<KeyValuePair<Long, Any>> { pair ->
            calls.incrementAndGet()
            sampleFields.fieldsOf(pair.data)
        }
        val index = KeyValueLuceneIndex(LongUtil(), counting)

        StepVerifier
            .create(index.add(KeyValuePair.create(Key.funKey(7007L), IndexedSample("ONCE", 1) as Any)))
            .verifyComplete()

        Assertions.assertThat(calls.get()).isEqualTo(1)
    }

    // Pins the second defect. The value fields are typed Any, so a field that
    // is not a String must reach lucene as text.
    @Test
    fun `a field value that is not a string indexes as text`() {
        val index = getIndex()
        val pair = KeyValuePair.create(Key.funKey(99L), IndexedSample("OTHER", 42) as Any)

        StepVerifier
            .create(
                index.add(pair)
                    .thenMany(index.findBy(IndexSearchRequest("size", "42", 10)))
            )
            .assertNext { key -> Assertions.assertThat(key.id).isEqualTo(99L) }
            .verifyComplete()
    }
}
