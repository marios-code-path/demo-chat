package com.demo.chat.test.key

import com.demo.chat.service.core.IKeyService
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

@Disabled
open class TestKeyServiceBase<T>(private val keyService: IKeyService<T>) {

    @Test
    fun `should create and find kind`() {
        StepVerifier
            .create(
                keyService
                    .key(String::class.java)
                    .flatMap { key ->
                        keyService.kind(key)
                    }
            )
            .assertNext { kind ->
                Assertions
                    .assertThat(kind)
                    .isNotNull()
                    .isEqualTo("String")
            }
            .verifyComplete()
    }

    @Test
    fun `should create key`() {
        StepVerifier
            .create(
                keyService
                    .key(Int::class.java)
            )
            .assertNext {
                Assertions
                    .assertThat(it)
                    .isNotNull
                    .hasNoNullFieldsOrProperties()
            }
            .verifyComplete()
    }

    @Test
    fun `should delete`() {
        val key = keyService.key(Int::class.java)
        val deleteStream = Mono
            .from(key)
            .flatMap(keyService::rem)

        StepVerifier
            .create(deleteStream)
            .verifyComplete()
    }

    @Test
    fun `create delete and not Exist`() {
        val keyStream = keyService
            .key(Int::class.java)
            .flatMap { k ->
                keyService
                    .rem(k)
                    .then(keyService.exists(k))
            }

        StepVerifier
            .create(keyStream)
            .assertNext {
                assertThat(it).isFalse()
            }
            .verifyComplete()
    }
}
