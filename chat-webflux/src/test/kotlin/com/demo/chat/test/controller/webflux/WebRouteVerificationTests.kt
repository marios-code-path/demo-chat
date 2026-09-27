package com.demo.chat.test.controller.webflux

import com.demo.chat.config.IndexServiceBeans
import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.controller.webflux.UserIndexRestController
import com.demo.chat.controller.webflux.UserPersistenceRestController
import com.demo.chat.controller.webflux.MessagePersistenceRestController
import com.demo.chat.domain.MessageSendRequest
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.test.TestGeneratorKeyService
import com.demo.chat.test.anyObject
import com.demo.chat.test.config.TestLongIndexBeans
import com.demo.chat.test.config.TestLongPersistenceBeans
import com.demo.chat.test.controller.webflux.config.WebFluxTestConfiguration
import com.demo.chat.test.key.FakeKeyServices
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest
import org.springframework.http.MediaType
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono

private val ROOTS = FakeKeyServices.longRoots()

/**
 * An index write verifies the entity key in the index domain. A forged key
 * never reaches the index. See `CHAT-avduuqwp`, D12.
 */
@WebFluxTest
@ExtendWith(SpringExtension::class)
@TestPropertySource(properties = ["app.controller.index"])
@ContextConfiguration(
    classes = [TestLongIndexBeans::class, LongTypeUtilConfiguration::class, UserIndexRestController::class, WebFluxTestConfiguration::class]
)
class IndexAddVerificationTests(@Autowired beans: IndexServiceBeans<Long, String, IndexSearchRequest>) {
    private val index = beans.userIndex()

    @Autowired
    private lateinit var registry: TestGeneratorKeyService<Long>

    @Autowired
    private lateinit var client: WebTestClient

    @BeforeEach
    fun `reset the index`() {
        Mockito.reset(index)
        BDDMockito.given(index.add(anyObject())).willReturn(Mono.empty())
    }

    private fun add(user: User<Long>) =
        client.put().uri("/index/user/add").contentType(MediaType.APPLICATION_JSON).bodyValue(user).exchange()

    @Test
    fun `a registered user reaches the index`() {
        val key = registry.register(4301L, ChatDomain.USER)

        add(User.create(key, "n", "h", "http://u")).expectStatus().isCreated
        Mockito.verify(index).add(anyObject())
    }

    @Test
    fun `an unknown user key never reaches the index`() {
        add(User.create(Key.of(424244L, ROOTS.of(ChatDomain.USER).id), "n", "h", "http://u")).expectStatus().isNotFound
        Mockito.verifyNoInteractions(index)
    }

    @Test
    fun `a forged root never reaches the index`() {
        val key = registry.register(4302L, ChatDomain.USER)

        add(User.create(Key.of(key.id, ROOTS.of(ChatDomain.MESSAGE).id), "n", "h", "http://u")).expectStatus().isNotFound
        Mockito.verifyNoInteractions(index)
    }
}

/**
 * A persistence read resolves its path id in the store domain. An unknown id
 * and an id of another domain never reach the store. See `CHAT-avduuqwp`, D2.
 */
@WebFluxTest
@ExtendWith(SpringExtension::class)
@TestPropertySource(properties = ["app.controller.persistence"])
@ContextConfiguration(
    classes = [TestLongPersistenceBeans::class, LongTypeUtilConfiguration::class, UserPersistenceRestController::class, WebFluxTestConfiguration::class]
)
class PersistenceGetVerificationTests(@Autowired beans: PersistenceServiceBeans<Long, String>) {
    private val store = beans.userPersistence()

    @Autowired
    private lateinit var registry: TestGeneratorKeyService<Long>

    @Autowired
    private lateinit var client: WebTestClient

    @BeforeEach
    fun `reset the store`() {
        Mockito.reset(store)
    }

    @Test
    fun `an unknown id never reaches the store`() {
        client.get().uri("/persist/user/get/424245").exchange().expectStatus().isNotFound
        Mockito.verifyNoInteractions(store)
    }

    @Test
    fun `an id of another domain never reaches the store`() {
        registry.register(4303L, ChatDomain.MESSAGE)

        client.get().uri("/persist/user/get/4303").exchange().expectStatus().isNotFound
        Mockito.verifyNoInteractions(store)
    }
}

/**
 * A message add resolves its sender in USER and its destination in
 * MESSAGE_TOPIC before the mint. A refused request neither mints nor writes.
 * See `CHAT-avduuqwp`, E8.
 */
@WebFluxTest
@ExtendWith(SpringExtension::class)
@TestPropertySource(properties = ["app.controller.persistence"])
@ContextConfiguration(
    classes = [TestLongPersistenceBeans::class, LongTypeUtilConfiguration::class, MessagePersistenceRestController::class, WebFluxTestConfiguration::class]
)
class MessageAddVerificationTests(@Autowired beans: PersistenceServiceBeans<Long, String>) {
    private val store = beans.messagePersistence()

    @Autowired
    private lateinit var registry: TestGeneratorKeyService<Long>

    @Autowired
    private lateinit var client: WebTestClient

    @BeforeEach
    fun `reset the store`() {
        Mockito.reset(store)
    }

    private fun add(from: Long, dest: Long) = client.put().uri("/persist/message/add")
        .contentType(MediaType.APPLICATION_JSON).bodyValue(MessageSendRequest("m", from, dest)).exchange()

    @Test
    fun `an unknown sender neither mints nor writes`() {
        val room = registry.register(4601L, ChatDomain.MESSAGE_TOPIC)

        add(424252L, room.id).expectStatus().isNotFound
        Mockito.verifyNoInteractions(store)
    }

    @Test
    fun `a destination outside MESSAGE_TOPIC neither mints nor writes`() {
        val user = registry.register(4602L, ChatDomain.USER)

        add(user.id, user.id).expectStatus().isNotFound
        Mockito.verifyNoInteractions(store)
    }
}
