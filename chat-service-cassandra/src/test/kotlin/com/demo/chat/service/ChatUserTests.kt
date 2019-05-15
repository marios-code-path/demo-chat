package com.demo.chat.service

import com.datastax.driver.core.utils.UUIDs
import com.demo.chat.ChatServiceApplication
import com.demo.chat.domain.ChatUser
import com.demo.chat.domain.ChatUserKey
import com.demo.chat.service.cassandra.CassandraConfiguration
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
import java.time.Instant

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
        val uuid = UUIDs.timeBased()
        val user = ChatUser(ChatUserKey(uuid, "Eddie"),
                "EddiesHandle", Instant.now())

        assertAll("user",
                { assertNotNull(user) },
                { assertEquals(uuid, user.key.userId) },
                { assertEquals("Eddie", user.key.handle) },
                { assertEquals("EddiesHandle", user.name) })

        StepVerifier
                .create(Flux.just(user))
                .assertNext { u ->
                    assertAll("simple user assertion",
                            { assertNotNull(u) },
                            { assertEquals(uuid, u.key.userId) }
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
        val chatUser = ChatUser(ChatUserKey(UUIDs.timeBased(), "vedder"), "eddie", Instant.now())

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
                .assertNext(::userAssertions)
                .verifyComplete()
    }

    @Test
    fun shouldPerformTruncateAndSave() {
        val chatUser = ChatUser(ChatUserKey(UUIDs.timeBased(), "vedder"), "eddie", Instant.now())

        val truncateAndSave = template
                .truncate(ChatUser::class.java)
                .thenMany(
                        Flux.just(chatUser)
                )
                .flatMap(template::insert)

        StepVerifier
                .create(truncateAndSave)
                .expectSubscription()
                .assertNext(::userAssertions)
                .verifyComplete()

    }

}