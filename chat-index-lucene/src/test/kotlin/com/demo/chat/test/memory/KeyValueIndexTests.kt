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

    // The replace removes by key. A uuid key holds separators, and the key
    // field is analyzed, so this proves the removal does not reach another
    // entity that shares a field value.
    @Test
    fun `a replaced uuid entry leaves another entity alone`() {
        val index = KeyValueLuceneIndex(UUIDUtil(), IndexEntryEncoder.ofKeyValueFields<UUID>(sampleFields))
        val first = Key.funKey(UUID.randomUUID())
        val second = Key.funKey(UUID.randomUUID())

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
