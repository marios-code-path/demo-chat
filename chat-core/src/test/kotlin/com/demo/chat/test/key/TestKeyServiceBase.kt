package com.demo.chat.test.key

import com.demo.chat.domain.Key
import com.demo.chat.domain.RootKeyDeletionException
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.service.core.IKeyService
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

/** The contract of every key service. See `CHAT-avduuqwp`. */
@Disabled
open class TestKeyServiceBase<T>(private val keyService: IKeyService<T>) {

    @Test
    fun `should mint under the root of the domain, and answer that root`() {
        StepVerifier
            .create(
                keyService
                    .key(ChatDomain.USER)
                    .flatMap { key -> keyService.rootOf(key.id).map { key to it } }
            )
            .assertNext { (key, root) ->
                assertThat(root).isEqualTo(key.root)
                assertThat(key.root).isNotEqualTo(key.id)
            }
            .verifyComplete()
    }

    @Test
    fun `two domains mint under two roots`() {
        StepVerifier
            .create(Mono.zip(keyService.key(ChatDomain.USER), keyService.key(ChatDomain.MESSAGE)))
            .assertNext { assertThat(it.t1.root).isNotEqualTo(it.t2.root) }
            .verifyComplete()
    }

    @Test
    fun `a root key answers itself, and it cannot be removed`() {
        StepVerifier
            .create(
                keyService.key(ChatDomain.USER)
                    .flatMap { key -> keyService.rootOf(key.root).map { key.root to it } }
            )
            .assertNext { (root, answer) -> assertThat(answer).isEqualTo(root) }
            .verifyComplete()

        StepVerifier
            .create(keyService.key(ChatDomain.USER).flatMap { keyService.rem(Key.root(it.root)) })
            .verifyError(RootKeyDeletionException::class.java)
    }

    @Test
    fun `should create key`() {
        StepVerifier
            .create(
                keyService
                    .key(ChatDomain.MESSAGE)
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
        val key = keyService.key(ChatDomain.MESSAGE)
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
            .key(ChatDomain.MESSAGE)
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
