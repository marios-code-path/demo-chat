package com.demo.chat.test.repository

import com.datastax.oss.driver.api.core.uuid.Uuids as UUIDs
import com.demo.chat.domain.Key
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.cassandra.ChatTopic
import com.demo.chat.domain.cassandra.ChatTopicKey
import com.demo.chat.domain.cassandra.ChatTopicName
import com.demo.chat.domain.cassandra.ChatTopicNameKey
import com.demo.chat.repository.cassandra.TopicByNameRepository
import com.demo.chat.repository.cassandra.TopicRepository
import com.demo.chat.test.CassandraSchemaTest
import com.demo.chat.test.CassandraTestConfiguration
import com.demo.chat.test.randomAlphaNumeric
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.Resource
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Flux
import reactor.core.publisher.switchIfEmpty
import reactor.test.StepVerifier
import java.util.*

@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [CassandraTestConfiguration::class])
class MessageTopicRepositoryTests : CassandraSchemaTest(){

    private val ROOMNAME = "XYZ"

    @Autowired
    lateinit var repo: TopicRepository<UUID>

    @Autowired
    lateinit var byNameRepo: TopicByNameRepository<UUID>

    @Test
    fun `inactive rooms dont appear`() {
        val roomId = UUID.randomUUID()
        val room = MessageTopic.create(Key.anyKey(roomId), ROOMNAME)

        val saveFlux = repo.add(room)

        val deleteMono = repo.rem(Key.anyKey(roomId))

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
                                    ChatTopic(it, randomAlphaNumeric(6), true)
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
        val saveFlux = Flux.just(Key.anyKey(UUIDs.timeBased()))
                .flatMap {
                    byNameRepo.save(ChatTopicName(ChatTopicNameKey(it.id, "XYZ"), true))
                }

        val findFlux = byNameRepo
                .findByKeyName("XYZ")

        val composed = Flux
                .from(saveFlux)
                .thenMany(findFlux)

        StepVerifier
                .create(composed)
                .assertNext {
                    roomAssertions(it)
                }
                .verifyComplete()
    }

    fun roomAssertions(room: MessageTopic<UUID>) {
        assertAll("room contents in tact",
                { Assertions.assertNotNull(room) },
                { Assertions.assertNotNull(room.key.id) },
                { Assertions.assertNotNull(room.data) })
    }
}