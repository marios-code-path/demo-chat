package com.demo.chat.test.repository

import com.demo.chat.domain.Key
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.cassandra.ChatTopic
import com.demo.chat.domain.cassandra.ChatTopicKey
import com.demo.chat.repository.cassandra.TopicRepository
import com.demo.chat.test.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Flux
import reactor.core.publisher.switchIfEmpty
import reactor.test.StepVerifier
import java.util.*
import com.datastax.oss.driver.api.core.uuid.Uuids as UUIDs

@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [CassandraTestConfiguration::class])
class TopicRepositoryTests : CassandraSchemaTest(){

    private val ROOMNAME = "XYZ"

    @Autowired
    lateinit var repo: TopicRepository<UUID>

    @Test
    fun `inactive rooms dont appear`() {
        val roomId = UUID.randomUUID()
        val room = MessageTopic.create(Key.funKey(roomId), ROOMNAME)

        val saveFlux = repo.add(room)

        val deleteMono = repo.rem(Key.funKey(roomId))

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
                .switchIfEmpty { throw (Exception("No Such Room")) }

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
                                    ChatTopic(it, TestBase.randomAlphaNumeric(6), true)
                                }
                                .flatMap {
                                    repo.add(it)
                                }
                                .thenMany(repo.findAll())
                )
                .expectSubscription()
                .assertNext(TestBase::topicAssertions)
                .assertNext(TestBase::topicAssertions)
                .verifyComplete()
    }
}