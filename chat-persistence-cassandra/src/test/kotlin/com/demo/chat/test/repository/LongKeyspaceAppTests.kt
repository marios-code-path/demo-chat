package com.demo.chat.test.repository

import org.junit.jupiter.api.BeforeAll

import com.demo.chat.service.core.IKeyGenerator

import com.demo.chat.service.core.RootKeyLoader

import com.demo.chat.service.core.RootKeyStore

import com.demo.chat.domain.knownkey.RootKeys

import com.demo.chat.domain.Key

import com.demo.chat.domain.knownkey.ChatDomain

import com.demo.chat.config.BaseDomainConfiguration
import com.demo.chat.config.persistence.cassandra.CorePersistenceServices
import com.demo.chat.domain.TypeUtil
import com.demo.chat.config.persistence.cassandra.CoreKeyServices
import com.demo.chat.config.persistence.cassandra.KeyGenConfiguration
import com.demo.chat.service.core.IKeyService
import com.demo.chat.test.CassandraSchemaTest
import com.demo.chat.test.TestLongKeyGenerator
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Tag
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
        BaseDomainConfiguration::class,
        KeyGenConfiguration::class,
        CoreKeyServices::class,
        CorePersistenceServices::class
    ]
)
@TestPropertySource(properties = ["app.service.core.key=cassandra","app.key.type=long","app.nodeid=1"])
@Tag("integration")
class LongKeyspaceAppTests : CassandraSchemaTest<Long>(TestLongKeyGenerator()) {

    @Autowired
    private lateinit var keyService: IKeyService<Long>

    @Autowired
    private lateinit var rootKeys: RootKeys<Long>

    @Autowired
    private lateinit var rootKeyStore: RootKeyStore<Long>

    @Autowired
    private lateinit var ids: IKeyGenerator<Long>

    /** A store node loads its roots at start. This test context has no start listener, so it loads them here. */
    @BeforeAll
    fun `load the roots`() {
        val roots = RootKeyLoader(rootKeyStore, ids).load().block()!!
        rootKeys.loadDomains(roots.mapValues { (_, id) -> Key.root(id) })
    }

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
            .create(keyService.key(ChatDomain.USER))
            .assertNext {
                Assertions
                    .assertThat(it)
                    .isNotNull
                    .hasFieldOrProperty("id")
            }
            .verifyComplete()


    }
}