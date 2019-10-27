package com.demo.chat.test.repository

import com.datastax.driver.core.utils.UUIDs
import com.demo.chat.domain.*
import com.demo.chat.repository.cassandra.ChatUserHandleRepository
import com.demo.chat.repository.cassandra.ChatUserRepository
import com.demo.chat.test.TestConfiguration
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
import reactor.test.StepVerifier
import java.time.Duration
import java.time.Instant
import java.util.*

@ExtendWith(SpringExtension::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [TestConfiguration::class])
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
    fun shouldContextLoad() {
        assertAll("Reactive Template Exists",
                { Assertions.assertNotNull(template) })
    }

    @Test
    fun `should search and find single user after insert many`() {
        val users = Flux.just(
                ChatUserHandle(ChatUserHandleKey(UUID.randomUUID(), "vedder"), "eddie", defaultImageUri, Instant.now()),
                ChatUserHandle(ChatUserHandleKey(UUID.randomUUID(), "darkbit"), "mario", defaultImageUri, Instant.now()))
                .flatMap {
                    handleRepo.save(it)
                }

        val find = handleRepo.findByKeyHandle("darkbit")

        val saveAndFind = Flux
                .from(users)
                .then(find)

        StepVerifier
                .create(saveAndFind)
                .expectSubscription()
                .assertNext { userStateAssertions(it, "darkbit", "mario") }
                .verifyComplete()
    }

    @Test
    fun `should reject same handle`() {
        val id1 = UUIDs.timeBased()
        val id2 = UUIDs.timeBased()

        val user1 =
                ChatUserHandle(ChatUserHandleKey(id1, "vedder"), "eddie1", defaultImageUri, Instant.now())
        val user2 =
                ChatUserHandle(ChatUserHandleKey(id2, "vedder"), "eddie2", defaultImageUri, Instant.now())


        val stream = template
                .truncate(ChatUserHandle::class.java)
                .then(handleRepo.add(user1))
                .then(handleRepo.add(user2))

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
                .flatMap { repo.add(it) }

        val stream = Flux
                .from(chatUsers)
                .thenMany(repo.findByKeyIdIn(Flux.just(id1, id2)))

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
                { Assertions.assertEquals(uuid, user.key.id) },
                { Assertions.assertEquals("Eddie", user.key.handle) },
                { Assertions.assertEquals("EddiesHandle", user.name) })

        StepVerifier
                .create(Flux.just(user))
                .assertNext { u ->
                    assertAll("simple user assertion",
                            { Assertions.assertNotNull(u) },
                            { Assertions.assertEquals(uuid, u.key.id) }
                    )
                }
                .verifyComplete()
    }

    @Test
    fun `should store and find single by ID`() {
        val userId = UUIDs.timeBased()

        val chatUser = ChatUser(
                ChatUserKey(userId, "vedder"),
                "eddie", defaultImageUri, Instant.now())

        val truncateAndSave = template
                .truncate(ChatUser::class.java)
                .then(repo.add(chatUser))

        val find = repo
                .findByKeyId(userId)

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
        val chatUsers = Flux.just(ChatUser(ChatUserKey(UUIDs.timeBased(), "vedder"), "eddie", defaultImageUri, Instant.now()))

        val truncateAndSave = template
                .truncate(ChatUser::class.java)
                .thenMany(chatUsers)
                .flatMap(template::insert)

        StepVerifier
                .create(truncateAndSave)
                .expectSubscription()
                .assertNext { userAssertions(it) }
                .verifyComplete()
    }

    fun userAssertions(user: User) {
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
                                                        Matchers.hasProperty("id")
                                                )
                                )
                        ))
    }


    // helper function to verify user state
    fun userStateAssertions(user: User, handle: String?, name: String?) {
        assertAll("User Assertion",
                { Assertions.assertNotNull(user) },
                { Assertions.assertNotNull(user.key.id) },
                { Assertions.assertNotNull(user.key.handle) },
                { Assertions.assertEquals(handle, user.key.handle) },
                { Assertions.assertEquals(name, user.name) }
        )
    }


}