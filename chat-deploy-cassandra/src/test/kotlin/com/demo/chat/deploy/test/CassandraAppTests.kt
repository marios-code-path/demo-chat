package com.demo.chat.deploy.test

import com.demo.chat.deploy.cassandra.BaseCassandraApp
import com.demo.chat.domain.TypeUtil
import com.demo.chat.service.IKeyService
import com.demo.chat.test.CassandraSchemaTest
import com.demo.chat.test.TestUUIDKeyGenerator
import com.demo.chat.test.TestUUIDKeyService
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.ParameterizedTypeReference
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.test.StepVerifier
import java.util.*

@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = [AppTestConfiguration::class, BaseCassandraApp::class]
)
class CassandraAppTests : CassandraSchemaTest<UUID>(TestUUIDKeyGenerator()) {
    //   @Autowired
    private var keyService: IKeyService<UUID> = TestUUIDKeyService()

    @Autowired
    private lateinit var typeUtil: TypeUtil<UUID>

    @Test
    fun `should application context load`() {
    }

    fun `test typeUtil is long`() {
        Assertions
            .assertThat(typeUtil)
            .isNotNull

        Assertions
            .assertThat(typeUtil.parameterizedType())
            .isEqualTo(ParameterizedTypeReference.forType<Long>(Long::class.java))
    }

    @Disabled
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