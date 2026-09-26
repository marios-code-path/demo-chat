package com.demo.chat.test.persistence.mock

import com.demo.chat.test.key.FakeKeyServices

import com.demo.chat.test.key.TestKeys

import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyValuePair
import com.demo.chat.persistence.cassandra.domain.CSKeyValuePair
import com.demo.chat.persistence.cassandra.domain.KVKey
import com.demo.chat.persistence.cassandra.impl.KeyValuePersistenceCassandra
import com.demo.chat.persistence.cassandra.repository.KeyValuePairRepository
import com.demo.chat.service.core.KeyValueStore
import com.demo.chat.test.TestBase
import com.demo.chat.test.TestLongKeyService
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito
import org.mockito.Mockito
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(SpringExtension::class)
class KeyValuePersistenceTests {

    lateinit var persistence: KeyValueStore<Long, Any>

    @MockitoBean
    lateinit var repo: KeyValuePairRepository<Long>

    @MockitoBean
    lateinit var template: ReactiveCassandraTemplate

    @MockitoBean
    lateinit var mapper: ObjectMapper

    private val keyService = TestLongKeyService()

    private val testData = KeyValuePair.create(TestKeys.key(1L), "test")
    private val testCSData = CSKeyValuePair(KVKey(1L), "test")

    @BeforeEach
    fun setUp() {
        BDDMockito
            .given(repo.findByKeyId(TestBase.anyObject()))
            .willReturn(Mono.just(testCSData))

        BDDMockito
            .given(repo.save(TestBase.anyObject()))
            .willReturn(Mono.empty<CSKeyValuePair<Long>>())

        BDDMockito
            .given(repo.findAll())
            .willReturn(Flux.just(testCSData))

        BDDMockito
            .given(repo.findByKeyIdIn(TestBase.anyObject()))
            .willReturn(Flux.just(testCSData))

        BDDMockito
            .given(repo.findAllById(Mockito.anyList()))
            .willReturn(Flux.just(testCSData))

        BDDMockito
            .given(repo.deleteByKeyId(Mockito.any(Long::class.java)))
            .willReturn(Mono.empty())

        BDDMockito
            .given(template.insert(Mockito.any(Any::class.java), Mockito.any()))
            .willReturn(Mono.empty())

        BDDMockito
            .given(mapper.writeValueAsString(TestBase.anyObject()))
            .willReturn("{test}")

        BDDMockito
            .given(mapper.readValue(Mockito.anyString(), Mockito.any(Class::class.java)))
            .willReturn("test")

        persistence = KeyValuePersistenceCassandra(TestLongKeyService(), FakeKeyServices.longRoots(), repo, mapper)
    }

    @Test
    fun `should all()`() {
        StepVerifier.create(persistence.all())
            .expectSubscription()
            .assertNext {
                Assertions
                    .assertThat(it)
                    .isNotNull
                    .hasFieldOrPropertyWithValue("data", "test")
            }
            .verifyComplete()
    }

    @Test
    fun `should rem()`() {
        StepVerifier.create(persistence.rem(TestKeys.key(1L)))
            .expectSubscription()
            .verifyComplete()
    }

    @Test
    fun `should get()`() {
        StepVerifier.create(persistence.get(TestKeys.key(1L)))
            .expectSubscription()
            .assertNext {
                Assertions
                    .assertThat(it)
                    .isNotNull
                    .hasFieldOrPropertyWithValue("data", "test")
            }

            .verifyComplete()
    }

    @Test
    fun `should add()`() {
        StepVerifier.create(persistence.add(testData))
            .expectSubscription()
            .verifyComplete()
    }

    @Test
    fun `should byIds()`() {
        StepVerifier.create(persistence.byIds(listOf(TestKeys.key(1L))))
            .expectSubscription()
            .assertNext {
                Assertions
                    .assertThat(it)
                    .isNotNull
                    .hasFieldOrPropertyWithValue("data", "test")
            }
            .verifyComplete()
    }
}