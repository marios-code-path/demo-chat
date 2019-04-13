package com.demo.chat.service

import com.demo.chat.ChatServiceApplication
import com.demo.chat.domain.ChatUser
import com.demo.chat.repository.ChatUserRepository
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
import java.sql.Time
import java.time.LocalTime
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

    @Test
    fun shouldFindDarkbit() {
        val findFlux = repo.findByHandle("darkbit")

        val setupAndFind = Flux
                .from(setUp(repo))
                .then(findFlux)

        StepVerifier
                .create(setupAndFind)
                .expectSubscription()
                .assertNext { userAssertions(it, "darkbit", "mario") }
                .verifyComplete()
    }

    @Test
    fun shouldFindVedder() {
        val findFlux = repo.findByName("eddie")

        val setupAndFind = Flux
                .from(setUp(repo))
                .thenMany(findFlux)


        StepVerifier
                .create(setupAndFind)
                .expectSubscription()
                .assertNext { userAssertions(it, "vedder", "eddie") }
                .verifyComplete()
    }

    // helper function to verify user state
    fun userAssertions(user: ChatUser, handle: String?, name: String?) {
        assertAll("User Assertion",
                { Assertions.assertNotNull(user) },
                { Assertions.assertNotNull(user.id) },
                { Assertions.assertNotNull(user.handle) },
                { Assertions.assertEquals(handle, user.handle) },
                { Assertions.assertEquals(name, user.name) }
        )
    }
}

fun setUp(repo: ChatUserRepository): Mono<Void> {

    val user1 = ChatUser(UUID.randomUUID(), "vedder", "eddie", Time.valueOf(LocalTime.now()))
    val user2 = ChatUser(UUID.randomUUID(), "darkbit", "mario", Time.valueOf(LocalTime.now()))

    return repo
            .insert(Flux.just(user1))
            .then(repo.insert(user2))
            .then()
}