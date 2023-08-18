package com.demo.chat.test.domain

import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.test.TestBase
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import reactor.test.StepVerifier
import reactor.test.publisher.TestPublisher

class UserTests : TestBase() {

    @Test
    fun `equality`() {
        val user1 = User.create(Key.funKey("KEY"), "TEST", "TEST", "TEST")
        val user2 = User.create(Key.funKey("KEY"), "TEST", "TEST", "TEST")

        Assertions.assertThat(user1).isEqualTo(user2)

        val user3 = User.create(Key.funKey("KEY2"), "TEST", "TEST", "TEST")

        Assertions.assertThat(user3).isNotEqualTo(user2)
    }

    @Test
    fun `should create`() {
        Assertions
                .assertThat(User.create(Key.funKey("KEY"), "TEST", "TEST", "TEST"))
                .isNotNull
                .hasNoNullFieldsOrProperties()
    }

    @Test
    fun `should test streaming only through publisher`() {
        val userPub = TestPublisher.create<User<out Any>>()
        val userFlux = userPub.flux()

        StepVerifier
                .create(userFlux)
                .expectSubscription()
                .then {
                    userPub.next(User.create(Key.funKey(3), "Test-User-3", "3", "http://"))
                    userPub.next(User.create(Key.funKey("FOO"), "Test-User-foo", "Foo", "http://"))
                }
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .isNotNull

                }
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .isNotNull
                }
                .then {
                    userPub.complete()
                }
                .verifyComplete()
    }
}