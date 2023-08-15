package com.demo.chat.test.repository.long

import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.persistence.cassandra.domain.CSKeyValuePair
import com.demo.chat.persistence.cassandra.domain.KVKey
import com.demo.chat.persistence.cassandra.repository.KeyValuePairRepository
import com.demo.chat.test.CassandraSchemaTest
import com.demo.chat.test.TestLongKeyGenerator
import com.demo.chat.test.repository.RepositoryTestConfiguration
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJson
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJsonTesters
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Hooks
import reactor.test.StepVerifier

@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = [RepositoryTestConfiguration::class]
)
@AutoConfigureJson
@AutoConfigureJsonTesters
@TestPropertySource(properties = ["app.key.type=long"])
class LongKeyValueRepositoryTests : CassandraSchemaTest<Long>(TestLongKeyGenerator()) {

    @Autowired
    private lateinit var repo: KeyValuePairRepository<Long>

    @Test
    fun `should save, find`() {
        Hooks.onOperatorDebug()

        val mapper: ObjectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build()).apply {
            propertyNamingStrategy = PropertyNamingStrategies.LOWER_CAMEL_CASE
            setSerializationInclusion(JsonInclude.Include.NON_NULL)
            findAndRegisterModules()
        }!!

        val user = User.create(Key.funKey(1L), "test", "test", "test")
        val userString = mapper.writeValueAsString(user)

        val kv = CSKeyValuePair(KVKey(1L), userString)

        StepVerifier
            .create(repo.save(kv))
            .expectSubscription()
            .expectNextCount(1)
            .verifyComplete()

        StepVerifier
            .create(repo.findAll())
            .expectSubscription()
            .assertNext {
                Assertions
                    .assertThat(it)
                    .isNotNull
            }
            .verifyComplete()
    }

    @Test
    fun `test save delete find`() {
        Hooks.onOperatorDebug()

        val mapper: ObjectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build()).apply {
            propertyNamingStrategy = PropertyNamingStrategies.LOWER_CAMEL_CASE
            setSerializationInclusion(JsonInclude.Include.NON_NULL)
            findAndRegisterModules()
        }!!

        val user = User.create(Key.funKey(1L), "test", "test", "test")
        val userString = mapper.writeValueAsString(user)

        val kv = CSKeyValuePair(KVKey(1L), userString)

        StepVerifier
            .create(repo.save(kv))
            .expectSubscription()
            .expectNextCount(1)
            .verifyComplete()

        StepVerifier
            .create(repo.deleteByKeyId(1L))
            .expectSubscription()
            .verifyComplete()

        StepVerifier
            .create(repo.findAll())
            .expectSubscription()
            .expectNextCount(0)
            .verifyComplete()
    }

    @Test
    fun `test save find by ID`() {
        Hooks.onOperatorDebug()

        val mapper: ObjectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build()).apply {
            propertyNamingStrategy = PropertyNamingStrategies.LOWER_CAMEL_CASE
            setSerializationInclusion(JsonInclude.Include.NON_NULL)
            findAndRegisterModules()
        }!!

        val user = User.create(Key.funKey(1L), "test", "test", "test")
        val userString = mapper.writeValueAsString(user)

        val kv = CSKeyValuePair(KVKey(1L), userString)

        StepVerifier
            .create(repo.save(kv))
            .expectSubscription()
            .expectNextCount(1)
            .verifyComplete()

        StepVerifier
            .create(repo.findByKeyId(1L))
            .expectSubscription()
            .expectNextCount(1)
            .verifyComplete()
    }
}