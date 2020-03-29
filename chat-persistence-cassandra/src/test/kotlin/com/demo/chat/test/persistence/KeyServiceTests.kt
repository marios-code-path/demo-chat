package com.demo.chat.test.persistence

import com.datastax.driver.core.utils.UUIDs
import com.demo.chat.codec.Codec
import com.demo.chat.domain.User

import com.demo.chat.service.IKeyService
import com.demo.chat.service.persistence.KeyServiceCassandra
import com.demo.chat.test.TestConfiguration
import org.assertj.core.api.Assertions
import org.cassandraunit.spring.CassandraDataSet
import org.cassandraunit.spring.CassandraUnit
import org.cassandraunit.spring.CassandraUnitDependencyInjectionTestExecutionListener
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import org.springframework.test.context.TestExecutionListeners
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.util.*

class TestUUIDKeyGeneratorCassandra : Codec<Unit, UUID> {
    override fun decode(record: Unit): UUID {
        return UUIDs.timeBased()
    }
}

@ExtendWith(SpringExtension::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [TestConfiguration::class])
@CassandraUnit
@TestExecutionListeners(CassandraUnitDependencyInjectionTestExecutionListener::class, DependencyInjectionTestExecutionListener::class)
@CassandraDataSet("simple-keys.cql")
class KeyServiceTests {
    private val logger = LoggerFactory.getLogger(this::class.simpleName)
    @Autowired
    private lateinit var template: ReactiveCassandraTemplate

    lateinit var svc: IKeyService<UUID>

    @BeforeEach
    fun setUp() {
        this.svc = KeyServiceCassandra(template, TestUUIDKeyGeneratorCassandra())
    }

    @Test
    fun `created key should Exist`() {
        val keyStream = svc
                .key(User::class.java)
                .flatMap(svc::exists)

        StepVerifier
                .create(keyStream)
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .isNotNull()
                            .isTrue()
                }
                .verifyComplete()
    }

    @Test
    fun `should create an key`() {
        val key = svc.key(User::class.java)

        StepVerifier
                .create(key)
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .isNotNull
                            .hasNoNullFieldsOrProperties()
                            .hasFieldOrProperty("id")
                }
                .verifyComplete()
    }

    @Test
    fun `should delete a key`() {
        val key = svc.key(User::class.java)
        val deleteStream = Flux
                .from(key)
                .flatMap(svc::rem)

        StepVerifier
                .create(deleteStream)
                .expectSubscription()
                .verifyComplete()
    }

    @Test
    fun `should create new UserKey`() {
        val userKey = svc.key(User::class.java)

        StepVerifier
                .create(userKey)
                .expectSubscription()
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .isNotNull
                            .hasNoNullFieldsOrProperties()

                    Assertions
                            .assertThat(it.id)
                            .isNotNull()
                }
                .verifyComplete()
    }
}