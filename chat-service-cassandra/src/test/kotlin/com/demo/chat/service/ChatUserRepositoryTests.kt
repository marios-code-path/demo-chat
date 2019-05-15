package com.demo.chat.service

import com.demo.chat.ChatServiceApplication
import com.demo.chat.domain.ChatUser
import com.demo.chat.domain.ChatUserKey
import com.demo.chat.domain.User
import com.demo.chat.domain.UserKey
import com.demo.chat.repository.cassandra.ChatUserHandleRepository
import com.demo.chat.repository.cassandra.ChatUserRepository
import com.demo.chat.service.cassandra.CassandraConfiguration
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

    @Test
    fun shouldFindDarkbit() {
        val findFlux = handleRepo.findByKeyHandle("darkbit")

        val setupAndFind = Flux
                .from(setUp(repo))
                .then(findFlux)

        StepVerifier
                .create(setupAndFind)
                .expectSubscription()
                .assertNext { userAssertions(it as User<UserKey>, "darkbit", "mario") }
                .verifyComplete()
    }

    // helper function to verify user state
    fun userAssertions(user: User<UserKey>, handle: String?, name: String?) {
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

    val user1 = ChatUser(ChatUserKey(UUID.randomUUID(), "vedder"), "eddie", Instant.now())
    val user2 = ChatUser(ChatUserKey(UUID.randomUUID(), "darkbit"), "mario", Instant.now())

    return repo
            .saveUsers(Flux.just(user1, user2))
            .then()
}