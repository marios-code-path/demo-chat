package com.demo.chat.test.controller.webflux.store

import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.config.persistence.memory.MemoryPersistenceServices
import com.demo.chat.controller.webflux.KeyValueStoreRestController
import com.demo.chat.controller.webflux.MembershipPersistenceRestController
import com.demo.chat.controller.webflux.MessagePersistenceRestController
import com.demo.chat.controller.webflux.core.mapping.KVRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.MembershipRequest
import com.demo.chat.domain.MessageSendRequest
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.test.TestGeneratorKeyService
import com.demo.chat.test.controller.webflux.LongTypeUtilConfiguration
import com.demo.chat.test.controller.webflux.config.WebFluxTestConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.MediaType
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * The REST add paths write through the real memory stores, which refuse a key
 * of another domain. See `CHAT-avduuqwp`, T5 step 6, E8 and E9.
 */
@WebFluxTest
@ExtendWith(SpringExtension::class)
@TestPropertySource(properties = ["app.controller.persistence", "app.service.core.persistence=memory"])
@ContextConfiguration(
    classes = [
        MemoryPersistenceServices::class, LongTypeUtilConfiguration::class, WebFluxTestConfiguration::class,
        MessagePersistenceRestController::class, MembershipPersistenceRestController::class, KeyValueStoreRestController::class,
    ]
)
class RestStoreWritePathTests {

    // The configuration class is registered, not built by hand. Its proxy
    // answers one store for each accessor, as a deployment does. The stores mint
    // through the slice registry, which is the one IKeyService bean here.

    @Autowired
    private lateinit var client: WebTestClient

    @Autowired
    private lateinit var registry: TestGeneratorKeyService<Long>

    @Autowired
    private lateinit var roots: RootKeys<Long>

    @Autowired
    private lateinit var stores: PersistenceServiceBeans<Long, String>

    private val keyType = object : ParameterizedTypeReference<Map<String, Any>>() {}

    private fun keyOf(body: Map<String, Any>): Key<Long> {
        @Suppress("UNCHECKED_CAST")
        val inner = (body["key"] ?: body) as Map<String, Any>
        return Key.of((inner["id"] as Number).toLong(), (inner["root"] as Number).toLong())
    }

    // E8
    @Test
    fun `a message add stores the message under a MESSAGE key`() {
        val user = registry.key(ChatDomain.USER).block()!!
        val room = registry.key(ChatDomain.MESSAGE_TOPIC).block()!!

        val body = client.put().uri("/persist/message/add").contentType(MediaType.APPLICATION_JSON)
            .bodyValue(MessageSendRequest("hello", user.id, room.id)).exchange()
            .expectStatus().isCreated.expectBody(keyType).returnResult().responseBody!!
        val key = keyOf(body)

        assertThat(key.root).isEqualTo(roots.of(ChatDomain.MESSAGE).id)
        val stored = stores.messagePersistence().get(key).block()!!
        assertThat(stored.key.from).isEqualTo(user.id)
        assertThat(stored.key.dest).isEqualTo(room.id)
    }

    // E8
    @Test
    fun `a membership add stores the membership under a TOPIC_MEMBERSHIP key`() {
        val user = registry.key(ChatDomain.USER).block()!!
        val room = registry.key(ChatDomain.MESSAGE_TOPIC).block()!!

        val body = client.put().uri("/persist/membership/add").contentType(MediaType.APPLICATION_JSON)
            .bodyValue(MembershipRequest(user.id, room.id)).exchange()
            .expectStatus().isCreated.expectBody(keyType).returnResult().responseBody!!
        val key = keyOf(body)

        assertThat(key.root).isEqualTo(roots.of(ChatDomain.TOPIC_MEMBERSHIP).id)
        val stored = stores.membershipPersistence().get(key).block()!!
        assertThat(stored.member).isEqualTo(user.id)
        assertThat(stored.memberOf).isEqualTo(room.id)
    }

    // E9
    @Test
    fun `a key value add stores the value under its KEY_VALUE_PAIR key`() {
        val key = registry.key(ChatDomain.KEY_VALUE_PAIR).block()!!

        client.put().uri("/persist/kv/add").contentType(MediaType.APPLICATION_JSON)
            .bodyValue(KVRequest(key.id, "value")).exchange()
            .expectStatus().isCreated

        assertThat(stores.keyValuePersistence().get(key).block()!!.data).isEqualTo("value")
    }
}
