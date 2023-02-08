package com.demo.chat.test.repository

import com.demo.chat.config.TypeUtilConfiguration
import com.demo.chat.persistence.cassandra.config.CorePersistenceServices
import com.demo.chat.domain.TypeUtil
import com.demo.chat.persistence.cassandra.config.CoreKeyServices
import com.demo.chat.persistence.cassandra.config.KeyGenConfiguration
import com.demo.chat.service.core.IKeyService
import com.demo.chat.test.CassandraSchemaTest
import com.demo.chat.test.TestLongKeyGenerator
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.ParameterizedTypeReference
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.test.StepVerifier

@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = [
        RepositoryTestConfiguration::class,
        TypeUtilConfiguration::class,
        KeyGenConfiguration::class,
        CoreKeyServices::class,
        CorePersistenceServices::class
    ]
)
@TestPropertySource(properties = ["app.service.core.key=long"])
class LongKeyspaceAppTests : CassandraSchemaTest<Long>(TestLongKeyGenerator()) {

    @Autowired
    private lateinit var keyService: IKeyService<Long>

    @Autowired
    private lateinit var typeUtil: TypeUtil<Long>

    @Test
    fun `should application context load`() {
    }

    @Test
    fun `test typeUtil is long`() {
        Assertions
            .assertThat(typeUtil)
            .isNotNull

        Assertions
            .assertThat(typeUtil.parameterizedType())
            .isEqualTo(ParameterizedTypeReference.forType<Long>(Long::class.java))
    }

    @Test
    fun `test key service is long`() {
        StepVerifier
            .create(keyService.key(Long::class.java))
            .assertNext {
                Assertions
                    .assertThat(it)
                    .isNotNull
                    .hasFieldOrProperty("id")
            }
            .verifyComplete()


    }
}