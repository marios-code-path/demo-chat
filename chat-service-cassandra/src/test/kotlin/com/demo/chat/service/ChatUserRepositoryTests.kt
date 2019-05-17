package com.demo.chat.service

import com.datastax.driver.core.utils.UUIDs
import com.demo.chat.ChatServiceApplication
import com.demo.chat.domain.ChatUser
import com.demo.chat.domain.ChatUserKey
import com.demo.chat.domain.User
import com.demo.chat.domain.UserKey
import com.demo.chat.repository.cassandra.ChatUserHandleRepository
import com.demo.chat.repository.cassandra.ChatUserRepository
import com.demo.chat.config.CassandraConfiguration
import org.cassandraunit.spring.CassandraDataSet
import org.cassandraunit.spring.CassandraUnit
import org.cassandraunit.spring.CassandraUnitDependencyInjectionTestExecutionListener
import org.junit.jupiter.api.Assertions
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
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant
import java.util.*

@ExtendWith(SpringExtension::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(CassandraConfiguration::class, ChatServiceApplication::class)
@CassandraUnit
@TestExecutionListeners(CassandraUnitDependencyInjectionTestExecutionListener::class, DependencyInjectionTestExecutionListener::class)
@CassandraDataSet("simple-user.cql")
class ChatUserRepositoryTests {
    @Autowired
    lateinit var repo: ChatUserRepository

    @Autowired
    lateinit var handleRepo: ChatUserHandleRepository

    @Autowired
    lateinit var template: ReactiveCassandraTemplate

    @Test
    fun shouldFindDarkbit() {
        val findFlux = handleRepo.findByKeyHandle("darkbit")

        val setupAndFind = Flux
                .from(setUp(repo))
                .then(findFlux)

        StepVerifier
                .create(setupAndFind)
                .expectSubscription()
                .assertNext { userStateAssertions(it as User<UserKey>, "darkbit", "mario") }
                .verifyComplete()
    }

    @Test
    fun `should user hold consistent states`() {
        val uuid = UUIDs.timeBased()
        val user = ChatUser(ChatUserKey(uuid, "Eddie"),
                "EddiesHandle", Instant.now())

        assertAll("user",
                { Assertions.assertNotNull(user) },
                { Assertions.assertEquals(uuid, user.key.userId) },
                { Assertions.assertEquals("Eddie", user.key.handle) },
                { Assertions.assertEquals("EddiesHandle", user.name) })

        StepVerifier
                .create(Flux.just(user))
                .assertNext { u ->
                    assertAll("simple user assertion",
                            { Assertions.assertNotNull(u) },
                            { Assertions.assertEquals(uuid, u.key.userId) }
                    )
                }
                .verifyComplete()
    }

    @Test
    fun shouldContextLoad() {
        assertAll("Reactive Template Exists",
                { Assertions.assertNotNull(template) })
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
                .assertNext{ userAssertions(it) }
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
                .assertNext { userAssertions(it) }
                .verifyComplete()

    }

}

fun setUp(repo: ChatUserRepository): Mono<Void> {

    val user1 = ChatUser(ChatUserKey(UUID.randomUUID(), "vedder"), "eddie", Instant.now())
    val user2 = ChatUser(ChatUserKey(UUID.randomUUID(), "darkbit"), "mario", Instant.now())

    return repo
            .saveUsers(Flux.just(user1, user2))
            .then()
}