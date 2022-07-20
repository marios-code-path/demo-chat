package com.demo.chat.deploy.test

import com.demo.chat.deploy.cassandra.BaseCassandraApp
import com.demo.chat.domain.TypeUtil
import com.demo.chat.service.IKeyService
import com.demo.chat.test.CassandraSchemaTest
import com.demo.chat.test.TestUUIDKeyGenerator
import com.demo.chat.test.TestUUIDKeyService
import com.demo.chat.test.repository.RepositoryTestConfiguration
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.ParameterizedTypeReference
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.test.StepVerifier
import java.util.*

@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = [AppTestConfiguration::class, BaseCassandraApp::class]
)
@TestPropertySource(properties = ["keyType=uuid", "app.service.core.key=uuid"])
class UUIDKeyspaceAppTests : CassandraSchemaTest<UUID>(TestUUIDKeyGenerator()) {
    @Autowired
    private lateinit var keyService: IKeyService<UUID>

    @Autowired
    private lateinit var typeUtil: TypeUtil<UUID>

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
            .isEqualTo(ParameterizedTypeReference.forType<UUID>(UUID::class.java))
    }

    @Test
    fun `test key service is long`() {
        StepVerifier
            .create(keyService.key(String::class.java))
            .assertNext {
                Assertions
                    .assertThat(it)
                    .isNotNull
                    .hasFieldOrProperty("id")
            }
            .verifyComplete()


    }
}