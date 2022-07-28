package com.demo.chat.test.repository.uuid

import com.demo.chat.domain.User
import com.demo.chat.domain.cassandra.ChatUser
import com.demo.chat.domain.cassandra.ChatUserKey
import com.demo.chat.repository.cassandra.ChatUserRepository
import com.demo.chat.test.CassandraSchemaTest
import com.demo.chat.test.TestUUIDKeyGenerator
import com.demo.chat.test.repository.RepositoryTestConfiguration
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.time.Duration
import java.time.Instant
import java.util.*

@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = [RepositoryTestConfiguration::class]
)
@TestPropertySource(properties = ["app.service.core.key=uuid"])
class UserRepositoryTests : CassandraSchemaTest<UUID>(TestUUIDKeyGenerator()) {
    @Autowired
    lateinit var repo: ChatUserRepository<UUID>

    @Test
    fun shouldContextLoad() {
        assertAll("Reactive Template Exists",
            { Assertions.assertNotNull(template) })
    }

    @Test
    fun `should find many by UUIDs`() {
        val id1 = keyGenerator.nextKey()
        val id2 = keyGenerator.nextKey()

        val chatUsers = Flux.just(
            ChatUser(ChatUserKey(id1), "eddie", "vedder", "http://path_to_file", Instant.now()),
            ChatUser(ChatUserKey(id2), "michael", "jackson", "http://path_to_file", Instant.now())
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
    fun `should pass simple state assertions`() {
        val uuid = keyGenerator.nextKey()
        val user = ChatUser(
            ChatUserKey(uuid), "Eddie",
            "EddiesHandle", "http://path_to_file", Instant.now()
        )

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
        val userId = keyGenerator.nextKey()

        val chatUser = ChatUser(
            ChatUserKey(userId),
            "3ddie", "v3dder", "http://path_to_file", Instant.now()
        )

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
    fun `should truncate and save`() {
        val chatUsers = Flux.just(
            ChatUser(
                ChatUserKey(keyGenerator.nextKey()),
                "eddie",
                "vedder",
                "http://path_to_file",
                Instant.now()
            )
        )

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
            .assertThat(
                "A User has key and properties", user,
                Matchers.allOf(
                    Matchers.notNullValue(),
                    Matchers.hasProperty("handle"),
                    Matchers.hasProperty("name", Matchers.not(Matchers.isEmptyOrNullString())),
                    Matchers.hasProperty(
                        "key",
                        Matchers
                            .allOf(
                                Matchers.notNullValue(),
                                Matchers.hasProperty("id")
                            )
                    )
                )
            )
    }
}