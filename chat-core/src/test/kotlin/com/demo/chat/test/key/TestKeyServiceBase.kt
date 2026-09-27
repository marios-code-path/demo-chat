package com.demo.chat.test.key

import com.demo.chat.domain.RootKeyDeletionException
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.core.IKeyService
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

/**
 * The contract of every key service. [rootKeys] holds the roots that the key
 * service mints under. See `CHAT-avduuqwp`.
 */
@Disabled
open class TestKeyServiceBase<T>(private val keyService: IKeyService<T>, private val rootKeys: RootKeys<T>) {

    @Test
    fun `mint sets the root of the domain`() {
        ChatDomain.entries.forEach { domain ->
            val key = keyService.key(domain).block()!!
            assertThat(key.root).isEqualTo(rootKeys.of(domain).id)
            assertThat(keyService.rootOf(key.id).block()).isEqualTo(key.root)
        }
    }

    @Test
    fun `rem refuses a root key`() {
        StepVerifier.create(keyService.rem(rootKeys.of(ChatDomain.USER))).verifyError(RootKeyDeletionException::class.java)
    }

    @Test
    fun `a removed key has no root`() {
        val key = keyService.key(ChatDomain.USER).block()!!
        keyService.rem(key).block()
        StepVerifier.create(keyService.rootOf(key.id)).verifyComplete()
    }

    @Test
    fun `a root key returns itself as its root`() {
        val root = rootKeys.of(ChatDomain.MESSAGE)
        assertThat(keyService.rootOf(root.id).block()).isEqualTo(root.id)
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
