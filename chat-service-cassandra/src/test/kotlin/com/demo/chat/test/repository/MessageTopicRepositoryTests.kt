package com.demo.chat.test.repository

import com.datastax.driver.core.utils.UUIDs
import com.demo.chat.domain.Key
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.cassandra.ChatMessageTopic
import com.demo.chat.domain.cassandra.ChatMessageTopicName
import com.demo.chat.domain.cassandra.ChatRoomNameKey
import com.demo.chat.domain.cassandra.ChatTopicKey
import com.demo.chat.repository.cassandra.TopicByNameRepository
import com.demo.chat.repository.cassandra.TopicRepository
import com.demo.chat.test.TestConfiguration
import com.demo.chat.test.randomAlphaNumeric
import org.cassandraunit.spring.CassandraDataSet
import org.cassandraunit.spring.CassandraUnit
import org.cassandraunit.spring.CassandraUnitDependencyInjectionTestExecutionListener
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestExecutionListeners
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.switchIfEmpty
import reactor.test.StepVerifier
import java.util.*

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [TestConfiguration::class])
@ImportAutoConfiguration
@CassandraUnit
@TestExecutionListeners(CassandraUnitDependencyInjectionTestExecutionListener::class, DependencyInjectionTestExecutionListener::class)
@CassandraDataSet("simple-room.cql")
class MessageTopicRepositoryTests {

    private val ROOMNAME = "XYZ"

    @Autowired
    lateinit var repo: TopicRepository

    @Autowired
    lateinit var byNameRepo: TopicByNameRepository

    @Test
    fun `inactive rooms dont appear`() {
        val roomId = UUID.randomUUID()
        val room = MessageTopic.create(Key.eventKey(roomId), ROOMNAME)

        val saveFlux = repo.add(room)

        val deleteMono = repo.rem(Key.eventKey(roomId))

        val findActiveRooms =
                repo.findAll()
                        .filter {
                            it.active
                        }

        val composed = Flux.from(saveFlux)
                .then(deleteMono)
                .thenMany(findActiveRooms)

        StepVerifier
                .create(composed)
                .expectSubscription()
                .expectNextCount(0)
                .verifyComplete()
    }

    @Test
    fun `should fail to find room`() {
        val queryFlux = repo
                .findByKeyId(UUID.randomUUID())
                .switchIfEmpty { Mono.error(Exception("No Such Room")) }

        StepVerifier
                .create(queryFlux)
                .expectSubscription()
                .expectError()
                .verify()
    }

    @Test
    fun `should save many and find as list`() {
        StepVerifier
                .create(
                        Flux.just(
                                ChatTopicKey(UUIDs.timeBased()),
                                ChatTopicKey(UUIDs.timeBased())
                        )
                                .map {
                                    ChatMessageTopic(it, randomAlphaNumeric(6), true)
                                }
                                .flatMap {
                                    repo.add(it)
                                }
                                .thenMany(repo.findAll())
                )
                .expectSubscription()
                .assertNext(this::roomAssertions)
                .assertNext(this::roomAssertions)
                .verifyComplete()
    }

    @Test
    fun `should save and find by name`() {
        val saveFlux = Flux.just(Key.eventKey(UUIDs.timeBased()))
                .flatMap {
                    byNameRepo.save(ChatMessageTopicName(ChatRoomNameKey(it.id, "XYZ"), true))
                }

        val findFlux = byNameRepo
                .findByKeyName("XYZ")

        val composed = Flux
                .from(saveFlux)
                .thenMany(findFlux)

        StepVerifier
                .create(composed)
                .assertNext { roomAssertions(it as MessageTopic) }
                .verifyComplete()
    }

    fun <R : MessageTopic> roomAssertions(room: R) {
        assertAll("room contents in tact",
                { Assertions.assertNotNull(room) },
                { Assertions.assertNotNull(room.key.id) },
                { Assertions.assertNotNull(room.data) })
    }
}