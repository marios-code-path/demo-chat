package com.demo.chat.test.controller.webflux

import com.demo.chat.config.KeyServiceBeans
import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.controller.webflux.IKeyRestController
import com.demo.chat.controller.webflux.UserPersistenceRestController
import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.test.anyObject
import com.demo.chat.test.config.TestLongKeyServiceBeans
import com.demo.chat.test.config.TestLongPersistenceBeans
import com.demo.chat.test.controller.webflux.config.WebFluxTestConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono
import java.util.concurrent.atomic.AtomicReference

@WebFluxTest
@ExtendWith(SpringExtension::class)
@TestPropertySource(properties = ["app.controller.persistence"])
@ContextConfiguration(
    classes = [
        TestLongPersistenceBeans::class,
        LongTypeUtilConfiguration::class,
        UserPersistenceRestController::class,
        WebFluxTestConfiguration::class
    ]
)
class PersistencePathVariableBindingTests(@Autowired beans: PersistenceServiceBeans<Long, String>) {

    private val store = beans.userPersistence()

    @Autowired
    private lateinit var client: WebTestClient

    @Test
    fun `get binds the path id as the declared key type`() {
        val requestedId = AtomicReference<Any?>()

        BDDMockito.given(store.get(anyObject()))
            .willAnswer { invocation ->
                requestedId.set((invocation.arguments[0] as Key<*>).id)
                Mono.just(User.create(Key.funKey(1001L), "userName", "userHandle", "imageUri"))
            }

        client
            .get()
            .uri("/persist/user/get/1001")
            .exchange()
            .expectStatus()
            .isOk

        assertThat(requestedId.get())
            .isInstanceOf(java.lang.Long::class.java)
            .isEqualTo(1001L)
    }

    @Test
    fun `rem binds the path id as the declared key type`() {
        val removedId = AtomicReference<Any?>()

        BDDMockito.given(store.rem(anyObject()))
            .willAnswer { invocation ->
                removedId.set((invocation.arguments[0] as Key<*>).id)
                Mono.empty<Void>()
            }

        client
            .delete()
            .uri("/persist/user/rem/1001")
            .exchange()
            .expectStatus()
            .isNoContent

        assertThat(removedId.get())
            .isInstanceOf(java.lang.Long::class.java)
            .isEqualTo(1001L)
    }
}

@WebFluxTest(IKeyRestController::class)
@ExtendWith(SpringExtension::class)
@TestPropertySource(properties = ["app.controller.key"])
@ContextConfiguration(
    classes = [
        TestLongKeyServiceBeans::class,
        LongTypeUtilConfiguration::class,
        IKeyRestController::class,
        WebFluxTestConfiguration::class
    ]
)
class KeyServicePathVariableBindingTests(@Autowired beans: KeyServiceBeans<Long>) {

    private val keyService = beans.keyService()

    @Autowired
    private lateinit var client: WebTestClient

    @Test
    fun `exists binds the path id as the declared key type`() {
        val checkedId = AtomicReference<Any?>()

        BDDMockito.given(keyService.exists(anyObject()))
            .willAnswer { invocation ->
                checkedId.set((invocation.arguments[0] as Key<*>).id)
                Mono.just(true)
            }

        client
            .get()
            .uri("/key/exists/1001")
            .exchange()
            .expectStatus()
            .isOk

        assertThat(checkedId.get())
            .isInstanceOf(java.lang.Long::class.java)
            .isEqualTo(1001L)
    }
}
