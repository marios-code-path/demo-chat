package com.demo.chat.service

import com.demo.chat.domain.UserKey
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import reactor.test.StepVerifier

class KeyServiceTests {

    private val svc: KeyService = TestKeyService

    @Test
    fun `should create new UserKey`() {
        val handle = "darkbit"
        val userKey = svc.key(UserKey::class.java) {
            UserKey.create(it.id, handle)
        }

        StepVerifier
                .create(userKey)
                .expectSubscription()
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .isNotNull
                            .hasNoNullFieldsOrProperties()

                    Assertions
                            .assertThat(it.handle)
                            .isNotNull()
                            .isEqualTo(handle)
                }
                .verifyComplete()
    }
}