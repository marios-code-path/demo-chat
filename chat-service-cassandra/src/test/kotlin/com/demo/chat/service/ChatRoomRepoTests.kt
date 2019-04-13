package com.demo.chat.service

import com.demo.chat.ChatServiceApplication
import com.demo.chat.domain.ChatRoom
import com.demo.chat.repository.ChatRoomRepository
import org.cassandraunit.spring.CassandraDataSet
import org.cassandraunit.spring.CassandraUnit
import org.cassandraunit.spring.CassandraUnitDependencyInjectionTestExecutionListener
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
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
import java.sql.Time
import java.time.LocalTime
import java.util.*

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(CassandraConfiguration::class, ChatServiceApplication::class)
@CassandraUnit
@TestExecutionListeners(CassandraUnitDependencyInjectionTestExecutionListener::class, DependencyInjectionTestExecutionListener::class)
@CassandraDataSet("simple-room.cql")
class ChatRoomRepoTests {

    @Autowired
    lateinit var repo: ChatRoomRepository

    @Autowired
    lateinit var template: ReactiveCassandraTemplate

    @Test
    fun `should fail to find room`() {
        val queryFlux = repo
                .findById(UUID.randomUUID())
                .switchIfEmpty { Mono.error(Exception("No Such Room")) }

        StepVerifier
                .create(queryFlux)
                .expectSubscription()
                .expectError()
                .verify()
    }

    @Test
    fun testShouldSaveFindByName() {
        val saveFlux = repo
                .insert(Flux.just(
                        ChatRoom(UUID.randomUUID(), "XYZ", Collections.emptySet(), Time.valueOf(LocalTime.now()))
                ))

        val findFlux = repo
                .findByName("XYZ")

        val composed = Flux
                .from(saveFlux)
                .thenMany(findFlux)

        StepVerifier
                .create(composed)
                .assertNext(this::roomAssertions)
                .verifyComplete()
    }

    @Test
    fun testShouldUpdateListAndVerify() {
        val roomId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        val saveFlux = repo
                .insert(Flux.just(
                        ChatRoom(roomId, "XYZ", Collections.emptySet(), Time.valueOf(LocalTime.now()))
                ))

        val updateFlux = template
                .update(Query.query(where("id").`is`(roomId)),
                        Update.of(listOf(Update.AddToOp(
                                ColumnName.from("members"),
                                listOf(userId),
                                Update.AddToOp.Mode.APPEND))),
                        ChatRoom::class.java
                )

        val findFlux = repo
                .findById(roomId)

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

    fun roomAssertions(room: ChatRoom) {
        assertAll("room contents in tact",
                { Assertions.assertNotNull(room) },
                { Assertions.assertNotNull(room.id) },
                { Assertions.assertNotNull(room.name) },
                { Assertions.assertNotNull(room.timestamp) }
        )
    }

}