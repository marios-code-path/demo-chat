package com.demo.chat.test.memory

import com.demo.chat.test.key.TestKeys

import com.demo.chat.domain.ByIdRequest
import com.demo.chat.domain.IndexSearchRequestConverters
import com.demo.chat.domain.Key
import com.demo.chat.domain.LongUtil
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.index.lucene.domain.IndexEntryEncoder
import com.demo.chat.index.lucene.impl.LuceneIndex
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

/**
 * A topic read through the production converter.
 *
 * The converter is the subject here. A test that built its own query would pass
 * whatever field the converter names, and that is the defect this test exists
 * to hold.
 */
class MessageTopicQueryTests {

    private val index = LuceneIndex<Long, Message<Long, String>>(
        IndexEntryEncoder.ofMessage(),
        { str -> TestKeys.key(LongUtil().fromString(str)) },
        { message -> message.key },
    )

    private val converters = IndexSearchRequestConverters()

    private fun message(id: Long, topic: Long, text: String) =
        Message.create(TestKeys.message(id, 10L, topic), text, true)

    @Test
    fun `a topic query returns only the messages of that topic`() {
        index.add(message(1L, 100L, "apple")).block()
        index.add(message(2L, 100L, "banana")).block()
        index.add(message(3L, 200L, "cherry")).block()

        val found = index
            .findBy(converters.topicIdToQuery(ByIdRequest(100L)))
            .collectList()
            .block()!!

        Assertions.assertThat(found.map { it.id }).containsExactlyInAnyOrder(1L, 2L)
    }

    @Test
    fun `the other topic returns its own message`() {
        index.add(message(1L, 100L, "apple")).block()
        index.add(message(3L, 200L, "cherry")).block()

        val found = index
            .findBy(converters.topicIdToQuery(ByIdRequest(200L)))
            .collectList()
            .block()!!

        Assertions.assertThat(found.map { it.id }).containsExactly(3L)
    }

    @Test
    fun `a topic with no message returns nothing`() {
        index.add(message(1L, 100L, "apple")).block()

        val found = index
            .findBy(converters.topicIdToQuery(ByIdRequest(999L)))
            .collectList()
            .block()!!

        Assertions.assertThat(found).isEmpty()
    }
}
