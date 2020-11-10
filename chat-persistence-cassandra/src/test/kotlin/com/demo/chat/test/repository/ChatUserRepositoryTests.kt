package com.demo.chat.test.repository

import com.datastax.driver.core.utils.UUIDs
import com.demo.chat.domain.User
import com.demo.chat.domain.cassandra.ChatUser
import com.demo.chat.domain.cassandra.ChatUserHandle
import com.demo.chat.domain.cassandra.ChatUserHandleKey
import com.demo.chat.domain.cassandra.ChatUserKey
import com.demo.chat.repository.cassandra.ChatUserHandleRepository
import com.demo.chat.repository.cassandra.ChatUserRepository
import com.demo.chat.test.CassandraSchemaTest
import com.demo.chat.test.CassandraTestConfiguration
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.Resource
import org.springframework.data.repository.reactive.ReactiveSortingRepository
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.function.Supplier

@Disabled
abstract class RepositoryTests<T, E>(val valueSupply: Supplier<E>,
val keySupply: Supplier<T>) {
    lateinit var repo: ReactiveSortingRepository<E, T>
    val defaultImageUri = "http://path_to_file"

    abstract fun assertElement(element: E) : Unit

    /**
     * org.assertj.core.api.Assertions
    .assertThat(element)
    .isNotNull
    .hasFieldOrProperty("key")
    .hasFieldOrProperty("handle")
    .hasFieldOrProperty("name")
    .extracting("key")
    .hasFieldOrProperty("id")
     */
    @Test
    fun <T> `should save && findById`() {
        val users = Flux.just(
                valueSupply.get(),
                valueSupply.get(),
                valueSupply.get())
                .flatMap {
                    repo.save(it)
                }

        val find = repo.findById(keySupply.get())

        val saveAndFind = Flux
                .from(users)
                .then(find)

        StepVerifier
                .create(saveAndFind)
                .expectSubscription()
                .assertNext (this::assertElement)
                .verifyComplete()
    }

    @Test
    fun `saveall && find all`() {
        val saveAndFind = repo
                .saveAll(Flux.just(valueSupply.get(), valueSupply.get()))
                .thenMany(repo.findAll())

        StepVerifier
                .create(saveAndFind)
                .assertNext (this::assertElement)
                .assertNext (this::assertElement)
                .verifyComplete()
    }

    @Test
    fun `should save and  findByID`() {
        val saveAndFindById = repo
                .save(valueSupply.get())
                .thenMany(repo.findById(keySupply.get()))

        StepVerifier
                .create(saveAndFindById)
                .expectSubscription()
                .assertNext (this::assertElement)
                .verifyComplete()
    }
}

@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [CassandraTestConfiguration::class])
class ChatUserRepositoryTests : CassandraSchemaTest(){
    @Autowired
    lateinit var repo: ChatUserRepository<UUID>

    @Autowired
    lateinit var handleRepo: ChatUserHandleRepository<UUID>

    val defaultImageUri = "http://path_to_file"

    @Value("classpath:simple-user.cql")
    override lateinit var cqlFile: Resource

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
                ChatUser(ChatUserKey(id1), "eddie", "vedder", defaultImageUri, Instant.now()),
                ChatUser(ChatUserKey(id2), "michael", "jackson", defaultImageUri, Instant.now())
        )
                .flatMap { repo.add(it) }

        val stream = Flux
                .from(chatUsers)
                .thenMany(repo.findByKeyIdIn(listOf(id1, id2)))

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
        val user = ChatUser(ChatUserKey(uuid), "Eddie",
                "EddiesHandle", defaultImageUri, Instant.now())

        assertAll("user",
                { Assertions.assertNotNull(user) },
                { Assertions.assertEquals(uuid, user.key.id) },
                { Assertions.assertEquals("Eddie", user.name) },
                { Assertions.assertEquals("EddiesHandle", user.handle) })

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
    fun `should save and find single by ID`() {
        val userId = UUIDs.timeBased()

        val chatUser = ChatUser(
                ChatUserKey(userId),
                "3ddie", "v3dder", defaultImageUri, Instant.now())

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
        val chatUsers = Flux.just(ChatUser(ChatUserKey(UUIDs.timeBased()), "eddie", "vedder", defaultImageUri, Instant.now()))

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

    fun userAssertions(user: User<UUID>) {
        MatcherAssert
                .assertThat("A User has key and properties", user,
                        Matchers.allOf(
                                Matchers.notNullValue(),
                                Matchers.hasProperty("handle"),
                                Matchers.hasProperty("name", Matchers.not(Matchers.isEmptyOrNullString())),
                                Matchers.hasProperty("key",
                                        Matchers
                                                .allOf(
                                                        Matchers.notNullValue(),
                                                        Matchers.hasProperty("id")
                                                )
                                )
                        ))
    }

    // helper function to verify user state
    fun userStateAssertions(user: User<UUID>, handle: String?, name: String?) {
        assertAll("User Assertion",
                { Assertions.assertNotNull(user) },
                { Assertions.assertNotNull(user.key.id) },
                { Assertions.assertNotNull(user.handle) },
                { Assertions.assertEquals(handle, user.handle) },
                { Assertions.assertEquals(name, user.name) }
        )
    }
}