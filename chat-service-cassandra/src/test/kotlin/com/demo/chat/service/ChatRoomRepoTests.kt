package com.demo.chat.service

import com.datastax.driver.core.utils.UUIDs
import com.demo.chat.config.CassandraConfiguration
import com.demo.chat.ChatServiceCassandraApp
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
import org.springframework.context.annotation.Import
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import org.springframework.data.cassandra.core.query.ColumnName
import org.springframework.data.cassandra.core.query.Query
import org.springframework.data.cassandra.core.query.Update
import org.springframework.data.cassandra.core.query.where
import org.springframework.test.context.TestExecutionListeners
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.switchIfEmpty
import reactor.test.StepVerifier
import java.time.Instant
import java.util.*

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes= [ChatServiceCassandraApp::class])
@ImportAutoConfiguration
@CassandraUnit
@TestExecutionListeners(CassandraUnitDependencyInjectionTestExecutionListener::class, DependencyInjectionTestExecutionListener::class)
@CassandraDataSet("simple-room.cql")
class ChatRoomRepoTests {

    @Autowired
    lateinit var repo: ChatRoomRepository

    @Autowired
    lateinit var byNameRepo: ChatRoomNameRepository

    @Autowired
    lateinit var template: ReactiveCassandraTemplate

    @Test
    fun `should fail to find room`() {
        val queryFlux = repo
                .findByKeyRoomId(UUID.randomUUID())
                .switchIfEmpty { Mono.error(Exception("No Such Room")) }

        StepVerifier
                .create(queryFlux)
                .expectSubscription()
                .expectError()
                .verify()
    }

    @Test
    fun `should save find by name`() {
        val saveFlux = repo
                .saveRooms(Flux.just(
                        ChatRoom(
                                ChatRoomKey(UUIDs.timeBased(), "XYZ"),
                                Collections.emptySet(),
                                Instant.now())
                ))

        val findFlux = byNameRepo
                .findByKeyName("XYZ")

        val composed = Flux
                .from(saveFlux)
                .thenMany(findFlux)

        StepVerifier
                .create(composed)
                .assertNext { roomAssertions(it as Room<RoomKey>) }
                //.expectNextCount(1)
                .verifyComplete()
    }

    @Test
    fun `should update members and verify`() {
        val roomId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        val saveFlux = repo
                .insert(Flux.just(
                        ChatRoom(
                                ChatRoomKey(
                                        roomId, "XYZ"),
                                Collections.emptySet(),
                                Instant.now())
                ))

        val updateFlux = template
                .update(Query.query(where("room_id").`is`(roomId)),
                        Update.of(listOf(Update.AddToOp(
                                ColumnName.from("members"),
                                listOf(userId),
                                Update.AddToOp.Mode.APPEND))),
                        ChatRoom::class.java
                )

        val findFlux = repo
                .findByKeyRoomId(roomId)

        val composed = Flux
                .from(saveFlux)
                .then(updateFlux)
                .thenMany(findFlux)

        StepVerifier
                .create(composed)
                .assertNext {
                    roomAssertions(it as Room<RoomKey>)
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

    fun roomAssertions(room: Room<RoomKey>) {
        assertAll("room contents in tact",
                { Assertions.assertNotNull(room) },
                { Assertions.assertNotNull(room.key.roomId) },
                { Assertions.assertNotNull(room.key.name) },
                { Assertions.assertNotNull(room.timestamp) }
        )
    }

}