package com.demo.chat.test.controller.webflux

import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.controller.webflux.KeyValueStoreRestController
import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyValuePair
import com.demo.chat.domain.LongUtil
import com.demo.chat.domain.TypeUtil
import com.demo.chat.test.anyObject
import com.demo.chat.test.config.TestLongPersistenceBeans
import com.demo.chat.test.controller.webflux.config.WebFluxTestConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.http.MediaType
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono
import java.util.concurrent.atomic.AtomicReference

@TestConfiguration
open class LongTypeUtilConfiguration {
    @Bean
    open fun typeUtil(): TypeUtil<Long> = LongUtil()
}

@WebFluxTest
@ExtendWith(SpringExtension::class)
@TestPropertySource(properties = ["app.controller.persistence"])
@ContextConfiguration(
    classes = [
        TestLongPersistenceBeans::class,
        LongTypeUtilConfiguration::class,
        KeyValueStoreRestController::class,
        WebFluxTestConfiguration::class
    ]
)
class KvRequestBindingTests(@Autowired beans: PersistenceServiceBeans<Long, String>) {

    private val store = beans.keyValuePersistence()

    @Autowired
    private lateinit var client: WebTestClient

    @Test
    fun `add binds the request key as the declared key type`() {
        val storedId = AtomicReference<Any?>()

        BDDMockito.given(store.add(anyObject()))
            .willAnswer { invocation ->
                storedId.set((invocation.arguments[0] as KeyValuePair<*, *>).key.id)
                Mono.empty<Void>()
            }

        BDDMockito.given(store.key())
            .willReturn(Mono.just(Key.funKey(1001L)))

        client
            .put()
            .uri("/persist/kv/add")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"key":1001,"data":"TestData"}""")
            .exchange()
            .expectStatus()
            .isCreated

        assertThat(storedId.get())
            .isInstanceOf(java.lang.Long::class.java)
            .isEqualTo(1001L)
    }
}
