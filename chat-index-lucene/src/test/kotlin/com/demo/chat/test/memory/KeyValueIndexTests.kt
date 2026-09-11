package com.demo.chat.test.memory

import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyValuePair
import com.demo.chat.domain.LongUtil
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
