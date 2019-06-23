package com.demo.chat.service

import com.datastax.driver.core.utils.UUIDs
import com.demo.chat.ChatServiceCassandraApp
import com.demo.chat.domain.ChatUser
import com.demo.chat.domain.ChatUserKey
import com.demo.chat.domain.User
import com.demo.chat.repository.cassandra.ChatUserHandleRepository
import com.demo.chat.repository.cassandra.ChatUserRepository
import com.demo.chat.repository.cassandra.MessageByUserRepository
import org.cassandraunit.spring.CassandraDataSet
import org.cassandraunit.spring.CassandraUnit
import org.cassandraunit.spring.CassandraUnitDependencyInjectionTestExecutionListener
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import org.springframework.test.context.TestExecutionListeners
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Duration
import java.time.Instant
import java.util.*

@ExtendWith(SpringExtension::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [ChatServiceCassandraApp::class])
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

    val defaultImageUri = "http://localhost:7070/s3"

    @Test
    fun shouldFindDarkbit() {
        val findFlux = handleRepo.findByKeyHandle("darkbit")

        val setupAndFind = Flux
                .from(setUp(repo))
                .then(findFlux)

        StepVerifier
                .create(setupAndFind)
                .expectSubscription()
                .assertNext { userStateAssertions(it, "darkbit", "mario") }
                .verifyComplete()
    }

    @Test
    fun `should reject same handle`() {
        val id1 = UUIDs.timeBased()
        val id2 = UUIDs.timeBased()

        val user1 =
                ChatUser(ChatUserKey(id1, "vedder"), "eddie1", defaultImageUri, Instant.now())
        val user2 =
                ChatUser(ChatUserKey(id2, "vedder"), "eddie2", defaultImageUri, Instant.now())


        val stream = template
                .truncate(ChatUser::class.java)
                .then(repo.saveUser(user1))
                .then(repo.saveUser(user2))
        //.thenMany(repo.saveUsers(Flux.just(user1, user2)))

        StepVerifier
                .create(stream)
                .expectSubscription()
                .expectError()
                .verify()
    }

    @Test
    fun `should find many by UUIDs`() {
        val id1 = UUIDs.timeBased()
        val id2 = UUIDs.timeBased()

        val chatUsers = Flux.just(
                ChatUser(ChatUserKey(id1, "vedder"), "eddie", defaultImageUri, Instant.now()),
                ChatUser(ChatUserKey(id2, "jackson"), "Michael", defaultImageUri, Instant.now())
        )

        val stream = template
                .truncate(ChatUser::class.java)
                .thenMany(repo.saveUsers(chatUsers))
                .thenMany(repo.findByKeyUserIdIn(Flux.just(id1, id2)))

        StepVerifier
                .create(stream)
                .assertNext {
                    userAssertions(it)
                }
                .assertNext {
                    userAssertions(it)
                }
                .expectComplete()
                .verify(Duration.ofMillis(2000))

    }

    @Test
    fun `should user hold consistent states`() {
        val uuid = UUIDs.timeBased()
        val user = ChatUser(ChatUserKey(uuid, "Eddie"),
                "EddiesHandle", defaultImageUri, Instant.now())

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
    fun `should store and find single by ID`() {
        val userId = UUIDs.timeBased()

        val chatUser = ChatUser(ChatUserKey(userId, "vedder"), "eddie", defaultImageUri, Instant.now())

        val truncateAndSave = template
                .truncate(ChatUser::class.java)
                .thenMany(Flux.just(chatUser))
                .flatMap(repo::saveUser)

        val find = repo
                .findByKeyUserId(userId)

        val composed = Flux
                .from(truncateAndSave)
                .then(find)

        StepVerifier
                .create(composed)
                .expectSubscription()
                .assertNext { userAssertions(it) }
                .verifyComplete()
    }

    @Test
    fun shouldPerformTruncateAndSave() {
        val chatUser = ChatUser(ChatUserKey(UUIDs.timeBased(), "vedder"), "eddie", defaultImageUri, Instant.now())

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


    fun userAssertions(user: ChatUser) {
        MatcherAssert
                .assertThat("A User has key and properties", user,
                        Matchers.allOf(
                                Matchers.notNullValue(),
                                Matchers.hasProperty("name", Matchers.not(Matchers.isEmptyOrNullString())),
                                Matchers.hasProperty("key",
                                        Matchers
                                                .allOf(
                                                        Matchers.notNullValue(),
                                                        Matchers.hasProperty("handle"),
                                                        Matchers.hasProperty("userId")
                                                )
                                )
                        ))
    }


    // helper function to verify user state
    fun userStateAssertions(user: User, handle: String?, name: String?) {
        assertAll("User Assertion",
                { Assertions.assertNotNull(user) },
                { Assertions.assertNotNull(user.key.userId) },
                { Assertions.assertNotNull(user.key.handle) },
                { Assertions.assertEquals(handle, user.key.handle) },
                { Assertions.assertEquals(name, user.name) }
        )
    }


}

fun setUp(repo: ChatUserRepository): Mono<Void> {
    val defaultImageUri = "http://localhost:7070/s3"

    val user1 = ChatUser(ChatUserKey(UUID.randomUUID(), "vedder"), "eddie", defaultImageUri, Instant.now())
    val user2 = ChatUser(ChatUserKey(UUID.randomUUID(), "darkbit"), "mario", defaultImageUri, Instant.now())

    return repo
            .saveUsers(Flux.just(user1, user2))
            .then()
}