package com.demo.chat.service

import com.datastax.driver.core.utils.UUIDs
import com.demo.chat.domain.ChatRoom
import com.demo.chat.domain.ChatRoomKey
import com.demo.chat.domain.Room
import com.demo.chat.domain.RoomKey
import com.demo.chat.repository.cassandra.ChatRoomNameRepository
import com.demo.chat.repository.cassandra.ChatRoomRepository
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
import java.time.Instant
import java.util.*

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [TestConfiguration::class])
@ImportAutoConfiguration
@CassandraUnit
@TestExecutionListeners(CassandraUnitDependencyInjectionTestExecutionListener::class, DependencyInjectionTestExecutionListener::class)
@CassandraDataSet("simple-room.cql")
class ChatRoomRepoTests {

    @Autowired
    lateinit var repo: ChatRoomRepository

    @Autowired
    lateinit var byNameRepo: ChatRoomNameRepository

    @Test
    fun `inactive rooms dont appear`() {
        val roomId = UUID.randomUUID()
        val room = Room.create(RoomKey.create(
                roomId, "XYZ"
        ), emptySet())

        val saveFlux = repo.add(room)

        val deactivateMono =
                repo.rem(RoomKey.create(roomId, "XYZ"))

        val findActiveRooms =
                repo.findAll()
                        .filter {
                            it.active
                        }

        val composed = Flux.from(saveFlux)
                .then(deactivateMono)
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
                                ChatRoom(
                                        ChatRoomKey(UUIDs.timeBased(), randomAlphaNumeric(6)),
                                        Collections.emptySet(),
                                        true,
                                        Instant.now()),
                                ChatRoom(
                                        ChatRoomKey(UUIDs.timeBased(), randomAlphaNumeric(6)),
                                        Collections.emptySet(),
                                        true,
                                        Instant.now())
                        ).flatMap {
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
        val saveFlux = Flux.just(
                Room.create(RoomKey.create(UUIDs.timeBased(), "XYZ"),
                        Collections.emptySet()))
                .flatMap {
                    repo.add(it)
                }

        val findFlux = byNameRepo
                .findByKeyName("XYZ")

        val composed = Flux
                .from(saveFlux)
                .thenMany(findFlux)

        StepVerifier
                .create(composed)
                .assertNext { roomAssertions(it as Room) }
                .verifyComplete()
    }

    @Test
    fun `should update members and verify`() {
        val roomId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        val saveFlux = repo
                .add(
                        ChatRoom(
                                ChatRoomKey(
                                        roomId, "XYZ"),
                                Collections.emptySet(),
                                true,
                                Instant.now())
                )

        val updateFlux = repo.join(userId, roomId)

        val findFlux = repo
                .findByKeyId(roomId)

        val composed = Flux
                .from(saveFlux)
                .then(updateFlux)
                .thenMany(findFlux)

        StepVerifier
                .create(composed)
                .assertNext {
                    roomAssertions(it)
                    assertAll("Room members contained",
                            { Assertions.assertTrue(it.members!!.isNotEmpty()) },
                            {
                                Assertions.assertEquals(userId,
                                        it.members!!.first())
                            }
                    )
                }
                .verifyComplete()
    }

    fun <R : Room> roomAssertions(room: R) {
        assertAll("room contents in tact",
                { Assertions.assertNotNull(room) },
                { Assertions.assertNotNull(room.key.id) },
                { Assertions.assertNotNull(room.key.name) },
                { Assertions.assertNotNull(room.timestamp) }
        )
    }

}