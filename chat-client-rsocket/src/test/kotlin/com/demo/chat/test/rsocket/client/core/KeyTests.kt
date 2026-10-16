package com.demo.chat.test.rsocket.client.core

import com.demo.chat.domain.knownkey.ChatDomain

import com.demo.chat.domain.UUIDUtil

import com.demo.chat.test.key.TestKeys

import com.demo.chat.client.rsocket.clients.core.KeyClient
import com.demo.chat.config.KeyServiceBeans
import com.demo.chat.controller.core.KeyServiceController
import com.demo.chat.domain.Key
import com.demo.chat.service.core.IKeyService
import com.demo.chat.service.core.KeyVerifier
import com.demo.chat.test.anyObject
import com.demo.chat.test.config.UUIDKeyServiceBeans
import com.demo.chat.test.rsocket.RSocketTestBase
import com.demo.chat.test.rsocket.RSocketTestRegistry
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.BDDMockito
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringJUnitConfig(
    classes = [
        UUIDKeyServiceBeans::class, TestKeyController::class
    ]
)
@Import(KeyTests.KeyVerifierTestConfiguration::class)
class KeyTests : RSocketTestBase() {
    @Autowired
    private lateinit var keyBeans: KeyServiceBeans<UUID>

    private val svcPrefix: String = "key."

    /**
     * This test names its own cause, on purpose. See `CHAT-ivtnvgyg`.
     *
     * One CI run answered this read with a completion that carried no value.
     * Three halves can produce that answer, and the log of that run cannot tell
     * them apart. This test can.
     *
     * 1. The server never reached [keyService]. The call check fails.
     * 2. The server reached [keyService] with a different key. The call check
     *    passes, and the key check fails.
     * 3. The server reached [keyService] with this key. Both reach checks
     *    pass, and the value check fails. So the answer was lost before the
     *    wire.
     *
     * The reach checks run after the value check on purpose. The answer of a
     * healthy run always arrives after the mock call, so the order adds no race.
     */
    @Test
    fun `client should call exists`() {
        val keyService = keyBeans.keyService()
        val key = TestKeys.key(UUID.randomUUID())

        BDDMockito
            .given(keyService.exists(anyObject()))
            .willReturn(Mono.just(true))

        val client: IKeyService<UUID> = KeyClient(svcPrefix, requester, UUIDUtil())

        val valueFailure = runCatching {
            StepVerifier
                .create(client.exists(key))
                .assertNext {
                    Assertions
                        .assertThat(it)
                        .isTrue()
                }
                .verifyComplete()
        }.exceptionOrNull()

        // The call check reads the call alone, and no argument. A check that
        // read the argument first would name a changed key as no call at all.
        val noCallFailure = runCatching {
            BDDMockito
                .then(keyService)
                .should(Mockito.atLeastOnce())
                .exists(anyObject())
        }.exceptionOrNull()

        if (noCallFailure != null) {
            throw AssertionError(
                "The server never reached the key service, so no route answered " +
                    "this read. See CHAT-ivtnvgyg.",
                noCallFailure
            )
        }

        val changedKeyFailure = runCatching {
            BDDMockito
                .then(keyService)
                .should()
                .exists(key)
        }.exceptionOrNull()

        if (changedKeyFailure != null) {
            throw AssertionError(
                "The server reached the key service with a different key, so the " +
                    "sent key did not survive the wire. See CHAT-ivtnvgyg.",
                changedKeyFailure
            )
        }

        valueFailure?.let { throw it }
    }

    /**
     * A removal answers no value. Its transport is `REQUEST_FNF`, so the client
     * completes when it sends the frame, before the server handles it.
     *
     * So the completion alone proves nothing. This test waits for the server to
     * reach [keyBeans], and the wait has a bound. See `CHAT-liomkoer`.
     */
    @Test
    fun `Client should remove a key`() {
        val keyService = keyBeans.keyService()
        val key = RSocketTestRegistry.registered(ChatDomain.MESSAGE)

        BDDMockito
            .given(keyService.rem(anyObject()))
            .willReturn(Mono.empty())

        val client: IKeyService<UUID> = KeyClient(svcPrefix, requester, UUIDUtil())

        StepVerifier
            .create(client.rem(key))
            .verifyComplete()

        BDDMockito
            .then(keyService)
            .should(Mockito.timeout(REMOVAL_BOUND_MILLIS))
            .rem(key)
    }

    @Test
    fun `Client should create a key`() {
        val keyService = keyBeans.keyService()
        val minted = TestKeys.key(UUID.randomUUID())

        BDDMockito
            .given(keyService.key(anyObject()))
            .willReturn(Mono.just(minted))

        val client: IKeyService<UUID> = KeyClient(svcPrefix, requester, UUIDUtil())

        StepVerifier
            .create(client.key(ChatDomain.USER))
            .assertNext {
                Assertions
                    .assertThat(it.id)
                    .isEqualTo(minted.id)
            }
            .verifyComplete()

        BDDMockito
            .then(keyService)
            .should()
            .key(ChatDomain.USER)
    }

    @TestConfiguration
    class KeyVerifierTestConfiguration {
        /**
         * The `key.rem` route carries `@Verified`, so this context owes it a
         * verifier. Without one the route answers no frame, and a test that
         * reads a completion alone cannot see that. See `CHAT-liomkoer`.
         */
        @Bean
        fun testKeyVerifier(): KeyVerifier<UUID> = RSocketTestRegistry.verifier
    }

    companion object {
        /** A bound on the wait for a removal to reach the service. */
        private const val REMOVAL_BOUND_MILLIS: Long = 2000
    }
}

@Controller
@MessageMapping("key")
// Every test here sends UUID keys. The type util is built here, so this scanned
// controller asks no new bean of any other context.
@Suppress("UNCHECKED_CAST")
class TestKeyController<T>(keyServices: KeyServiceBeans<T>) :
    KeyServiceController<T>(keyServices.keyService(), UUIDUtil() as com.demo.chat.domain.TypeUtil<T>)