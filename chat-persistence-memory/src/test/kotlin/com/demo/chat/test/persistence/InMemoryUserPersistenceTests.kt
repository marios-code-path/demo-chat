package com.demo.chat.test.persistence

import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.service.core.UserPersistence
import com.demo.chat.persistence.memory.impl.UserPersistenceInMemory
import com.demo.chat.test.TestLongKeyService
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import reactor.test.StepVerifier


class InMemoryUserPersistenceTests {
    private val persistence: UserPersistence<Long> = UserPersistenceInMemory(
        TestLongKeyService()
    ) { user -> user.key }


    @Test
    fun `should not add user with same handle`() {
        val user = User.create(Key.funKey(1L), "TEST", "HANDLE", "HTTP")

        val publisher = persistence
            .add(user)
            .then(persistence.add(user))

        StepVerifier
            .create(publisher)
            .`as`("First success, then second will throw")
            .expectError()
            .verify()

        StepVerifier.create(persistence.add(user))
            .`as`("Another adduser will throw")
            .expectError()
            .verify()

        StepVerifier
            .create(persistence.all().count())
            .assertNext {
                Assertions
                    .assertThat(it)
                    .isNotNull
                    .isEqualTo(1L)
            }
            .verifyComplete()
    }
}