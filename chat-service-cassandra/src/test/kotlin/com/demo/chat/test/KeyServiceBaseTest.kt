package com.demo.chat.test

import com.demo.chat.domain.UserKey
import com.demo.chat.service.KeyService
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Flux
import reactor.test.StepVerifier

@ExtendWith(SpringExtension::class)
open class KeyServiceBaseTest {

    lateinit var svc: KeyService

    private val handle = "darkbit"

    @Test
    fun `created key should Exist`() {
        val keyStream = svc
                .id(UserKey::class.java)
                .flatMap(svc::exists)

        StepVerifier
                .create(keyStream)
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .isNotNull()
                            .isTrue()
                }
                .verifyComplete()
    }

    @Test
    fun `should create an key`() {
        val key = svc.id(UserKey::class.java)

        StepVerifier
                .create(key)
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .isNotNull
                            .hasNoNullFieldsOrProperties()
                            .hasFieldOrProperty("id")
                }
                .verifyComplete()
    }

    @Test
    fun `should delete a key`() {
        val key = svc.id(UserKey::class.java)
        val deleteStream = Flux
                .from(key)
                .flatMap(svc::rem)

        StepVerifier
                .create(deleteStream)
                .expectSubscription()
                .verifyComplete()
    }

    @Test
    fun `should create new UserKey`() {
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