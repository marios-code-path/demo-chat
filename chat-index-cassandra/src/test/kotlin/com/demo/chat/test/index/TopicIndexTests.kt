package com.demo.chat.test.index

import com.demo.chat.domain.MessageTopic
import com.demo.chat.index.cassandra.domain.ChatTopicName
import com.demo.chat.index.cassandra.domain.ChatTopicNameKey
import com.demo.chat.index.cassandra.repository.TopicByNameRepository
import com.demo.chat.service.core.IndexService
import com.demo.chat.service.core.TopicIndexService
import com.demo.chat.service.core.TopicIndexService.Companion.NAME
import com.demo.chat.index.cassandra.impl.TopicIndex
import com.demo.chat.test.anyObject
import com.demo.chat.test.randomAlphaNumeric
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(SpringExtension::class)
class TopicIndexTests {

    @MockBean
    lateinit var nameRepo: TopicByNameRepository<UUID>

    lateinit var topicIndex: TopicIndexService<UUID, Map<String, String>>

    private val testTopicId = UUID.randomUUID()
    private val testTopicName = "TEST_TOPIC"
    private val topicByName = ChatTopicName(ChatTopicNameKey(testTopicId, testTopicName), true)

    fun getIndex(): IndexService<UUID, MessageTopic<UUID>, Map<String, String>> = topicIndex

    @BeforeEach
    fun setUp() {
        topicIndex = TopicIndex(nameRepo)
    }


    @Test
    fun `should search by topic name`() {

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
}