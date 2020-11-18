package com.demo.chat.test.index

import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.cassandra.ChatTopic
import com.demo.chat.domain.cassandra.ChatTopicKey
import com.demo.chat.domain.cassandra.ChatTopicName
import com.demo.chat.domain.cassandra.ChatTopicNameKey
import com.demo.chat.repository.cassandra.TopicByNameRepository
import com.demo.chat.repository.cassandra.TopicRepository
import com.demo.chat.service.IndexService
import com.demo.chat.service.TopicIndexService
import com.demo.chat.service.TopicIndexService.Companion.ALL
import com.demo.chat.service.TopicIndexService.Companion.NAME
import com.demo.chat.service.index.TopicCriteriaCodec
import com.demo.chat.service.index.TopicIndexCassandra
import com.demo.chat.test.anyObject
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.*

private val ALPHA_NUMERIC_STRING = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

fun randomAlphaNumeric(size: Int): String {
    var count = size
    val builder = StringBuilder()
    while (count-- != 0) {
        val character = (Math.random() * ALPHA_NUMERIC_STRING.length).toInt()
        builder.append(ALPHA_NUMERIC_STRING[character])
    }
    return builder.toString()
}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(SpringExtension::class)
class TopicIndexTests {
//class TopicIndexTests : IndexTestBase<UUID, MessageTopic<UUID>, Map<String, String>>
//(Supplier { MessageTopic.create(Key.funKey(UUID(10L, 10L)), "TEST_TOPIC") },
//        Supplier { Key.funKey(UUID(10L, 10L)) },
//        Supplier { mapOf(Pair(ALL, "")) }) {

    @MockBean
    lateinit var roomRepo: TopicRepository<UUID>

    @MockBean
    lateinit var nameRepo: TopicByNameRepository<UUID>

    lateinit var topicIndex: TopicIndexService<UUID>

    private val testTopicId = UUID.randomUUID()
    private val testTopicName = "TEST_TOPIC"
    private val topicByKey = ChatTopic(ChatTopicKey(testTopicId), testTopicName, true)
    private val topicByName = ChatTopicName(ChatTopicNameKey(testTopicId, testTopicName), true)

    fun getIndex(): IndexService<UUID, MessageTopic<UUID>, Map<String, String>> = topicIndex

    @BeforeEach
    fun setUp() {
        topicIndex = TopicIndexCassandra(TopicCriteriaCodec(), roomRepo, nameRepo)
    }


    @Test
    fun `should search by topic name`() {
        BDDMockito
                .given(roomRepo.save(anyObject<ChatTopic<UUID>>()))
                .willReturn(Mono.empty())

        BDDMockito.given(nameRepo.save(anyObject<ChatTopicName<UUID>>()))
                .willReturn(Mono.just(topicByName))

        BDDMockito.given(nameRepo.findByKeyName(anyObject()))
                .willReturn(Mono.just(topicByName))

        StepVerifier
                .create(
                        topicIndex.findBy(mapOf(Pair(NAME, randomAlphaNumeric(5))))
                )
                .expectSubscription()
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .isNotNull
                            .hasNoNullFieldsOrProperties()
                            .extracting("id")
                            .isInstanceOf(UUID::class.java)
                }
                .verifyComplete()
    }

    @Test
    fun `should search all`() {
        BDDMockito.given(roomRepo.findAll())
                .willReturn(Flux.just(topicByKey, topicByKey))

        StepVerifier
                .create(
                        topicIndex.findBy(mapOf(Pair(ALL, "")))
                )
                .expectSubscription()
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .isNotNull
                            .hasNoNullFieldsOrProperties()
                            .extracting("id")
                            .isInstanceOf(UUID::class.java)
                }
                .assertNext {} // We know you're the same
                .verifyComplete()
    }
}