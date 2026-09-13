package com.demo.chat.test.domain

import com.demo.chat.domain.ByIdRequest
import com.demo.chat.domain.IndexSearchRequestConverters
import com.demo.chat.domain.MapRequestConverters
import com.demo.chat.service.core.MessageIndexService
import com.demo.chat.service.core.TopicIndexService
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

/**
 * The field that a topic query names.
 *
 * `MessagingServiceImpl.listenTopic` is the one consumer, and it reads the
 * message index. That index stores a message destination under
 * `MessageIndexService.TOPIC`. A query on the topic index field matched no
 * message document on either backend.
 */
class TopicQueryConverterTests {

    private val searchConverters = IndexSearchRequestConverters()
    private val mapConverters = MapRequestConverters()

    @Test
    fun `the lucene converter names the message topic field`() {
        val query = searchConverters.topicIdToQuery(ByIdRequest(30L))

        Assertions.assertThat(query.first).isEqualTo(MessageIndexService.TOPIC)
        Assertions.assertThat(query.second).isEqualTo("30")
        Assertions.assertThat(query.config).isEqualTo(100)
    }

    // The two constants differ only in case, and lucene field names are case
    // sensitive. So a test that compared them loosely would pass either way.
    @Test
    fun `the lucene converter does not name the topic index field`() {
        val query = searchConverters.topicIdToQuery(ByIdRequest(30L))

        Assertions.assertThat(query.first).isNotEqualTo(TopicIndexService.ID)
    }

    // The cassandra message index reads query.keys.first(), so the order of
    // this map decides which branch runs.
    @Test
    fun `the map converter leads with the message topic field`() {
        val query = mapConverters.topicIdToQuery(ByIdRequest(30L))

        Assertions.assertThat(query.keys.first()).isEqualTo(MessageIndexService.TOPIC)
        Assertions.assertThat(query[MessageIndexService.TOPIC]).isEqualTo("30")
        Assertions.assertThat(query["SAMPLE_SIZE"]).isEqualTo("100")
    }

    @Test
    fun `the map converter does not name the topic index field`() {
        val query = mapConverters.topicIdToQuery(ByIdRequest(30L))

        Assertions.assertThat(query).doesNotContainKey(TopicIndexService.ID)
    }

    // Only the message query moved. A topic name still reads the topic index.
    @Test
    fun `a topic name query still names the topic index field`() {
        Assertions
            .assertThat(searchConverters.topicNameToQuery(com.demo.chat.domain.ByStringRequest("room")).first)
            .isEqualTo(TopicIndexService.NAME)
        Assertions
            .assertThat(mapConverters.topicNameToQuery(com.demo.chat.domain.ByStringRequest("room")).keys.first())
            .isEqualTo(TopicIndexService.NAME)
    }
}
