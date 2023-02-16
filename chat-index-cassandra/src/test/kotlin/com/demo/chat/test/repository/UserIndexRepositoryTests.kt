package com.demo.chat.test.repository

import com.datastax.oss.driver.api.core.uuid.Uuids
import com.demo.chat.index.cassandra.domain.ChatUserHandle
import com.demo.chat.index.cassandra.domain.ChatUserHandleKey
import com.demo.chat.index.cassandra.repository.ChatUserHandleRepository
import com.demo.chat.test.CassandraSchemaTest
import com.demo.chat.test.IndexRepositoryTestConfiguration
import com.demo.chat.test.TestBase
import com.demo.chat.test.TestUUIDKeyGenerator
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
import java.time.Instant
import java.util.*

@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = [IndexRepositoryTestConfiguration::class]
)
@TestPropertySource(properties = ["app.key.type=uuid"])
class UserIndexRepositoryTests : CassandraSchemaTest<UUID>(TestUUIDKeyGenerator()) {

    @Autowired
    lateinit var handleRepo: ChatUserHandleRepository<UUID>

    @Test
    fun shouldContextLoad() {
        assertAll("Reactive Template Exists",
            { Assertions.assertNotNull(template) })
    }

    @Test
    fun `should search and find single user after insert many`() {
        val users = Flux.just(
            ChatUserHandle(
                ChatUserHandleKey(UUID.randomUUID(), "vedder"),
                "eddie",
                "http://path_to_file",
                Instant.now()
            ),
            ChatUserHandle(
                ChatUserHandleKey(UUID.randomUUID(), "darkbit"),
                "mario",
                "http://path_to_file",
                Instant.now()
            )
        )
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
            .assertNext { TestBase.userAssertions(it, "darkbit", "mario") }
            .verifyComplete()
    }

    @Test
    fun `should reject same handle`() {
        val id1 = Uuids.timeBased()
        val id2 = Uuids.timeBased()

        val user1 =
            ChatUserHandle(ChatUserHandleKey(id1, "vedder"), "eddie1", "http://path_to_file", Instant.now())
        val user2 =
            ChatUserHandle(ChatUserHandleKey(id2, "vedder"), "eddie2", "http://path_to_file", Instant.now())

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
}