package com.demo.chat.service

import com.demo.chat.ChatServiceApplication
import com.demo.chat.domain.ChatUser
import org.cassandraunit.spring.CassandraDataSet
import org.cassandraunit.spring.CassandraUnit
import org.cassandraunit.spring.CassandraUnitDependencyInjectionTestExecutionListener
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import org.springframework.test.context.TestExecutionListeners
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.sql.Time
import java.time.LocalTime
import java.util.*

@ExtendWith(SpringExtension::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(CassandraConfiguration::class, ChatServiceApplication::class)
@CassandraUnit
@TestExecutionListeners(CassandraUnitDependencyInjectionTestExecutionListener::class, DependencyInjectionTestExecutionListener::class)
@CassandraDataSet("simple-user.cql")
class ChatUserTests {

    @Autowired
    lateinit var template: ReactiveCassandraTemplate

    @Test
    fun testShouldUserCreateAndReactivate() {
        val uuid = UUID.randomUUID()
        val user = ChatUser(uuid, "Eddie", "EddiesHandle", Time.valueOf(LocalTime.now()))

        assertAll("user",
                { assertNotNull(user) },
                { assertEquals(uuid, user.id) },
                { assertEquals("Eddie", user.handle) },
                { assertEquals("EddiesHandle", user.name) })

        StepVerifier
                .create(Flux.just(user))
                .assertNext { u ->
                    assertAll("simple user assertion",
                            { assertNotNull(u) },
                            { assertEquals(uuid, u.id) }
                    )
                }
                .verifyComplete()
    }

    @Test
    fun shouldContextLoad() {
        assertAll("Reactive Template Exists",
                { assertNotNull(template) })
    }

    @Test
    fun shouldPerformSaveCrudFind() {
        val chatUser = ChatUser(UUID.randomUUID(), "vedder", "eddie", Time.valueOf(LocalTime.now()))

        val truncateAndSave = template
                .truncate(ChatUser::class.java)
                .thenMany(Flux.just(chatUser))
                .flatMap(template::insert)

        val find = template
                .query(ChatUser::class.java)
                .one()

        val composed = Flux
                .from(truncateAndSave)
                .then(find)

        StepVerifier
                .create(composed)
                .expectSubscription()
                .assertNext(this::userAssertions)
                .verifyComplete()
    }

    @Test
    fun shouldPerformTruncateAndSave() {
        val chatUser = ChatUser(UUID.randomUUID(), "vedder", "eddie", Time.valueOf(LocalTime.now()))

        val truncateAndSave = template
                .truncate(ChatUser::class.java)
                .thenMany(
                        Flux.just(chatUser)
                )
                .flatMap(template::insert)

        StepVerifier
                .create(truncateAndSave)
                .expectSubscription()
                .assertNext(this::userAssertions)
                .verifyComplete()

    }

    fun userAssertions(user: ChatUser) {
        assertAll("User Assertion",
                { assertNotNull(user) },
                { assertNotNull(user.id) },
                { assertNotNull(user.handle) },
                { assertEquals("vedder", user.handle) },
                { assertEquals("eddie", user.name) }
        )
    }

}