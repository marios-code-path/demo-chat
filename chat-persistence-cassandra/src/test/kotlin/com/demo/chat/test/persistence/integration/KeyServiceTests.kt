package com.demo.chat.test.persistence.integration

import com.demo.chat.test.key.FakeKeyServices

import com.demo.chat.domain.RootKeyDeletionException

import com.demo.chat.domain.knownkey.ChatDomain

import com.demo.chat.domain.User
import com.demo.chat.persistence.cassandra.impl.KeyServiceCassandra
import com.demo.chat.service.core.IKeyService
import com.demo.chat.test.CassandraSchemaTest
import com.demo.chat.test.TestUUIDKeyGenerator
import com.demo.chat.test.repository.RepositoryTestConfiguration
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.util.*

@ExtendWith(SpringExtension::class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = [RepositoryTestConfiguration::class]
)
@TestPropertySource(properties = ["app.service.core.key=cassandra", "app.key.type=uuid"])
@Tag("integration")
class KeyServiceTests : CassandraSchemaTest<UUID>(TestUUIDKeyGenerator()) {
    lateinit var svc: IKeyService<UUID>
    private val rootKeys = FakeKeyServices.uuidRoots()

    @BeforeAll
    fun setUp() {
        this.svc = KeyServiceCassandra(template, keyGenerator, rootKeys)
    }

    @Test
    fun `mint sets the root of the domain`() {
        ChatDomain.entries.forEach { domain ->
            val key = svc.key(domain).block()!!
            Assertions.assertThat(key.root).isEqualTo(rootKeys.of(domain).id)
            Assertions.assertThat(svc.rootOf(key.id).block()).isEqualTo(key.root)
        }
    }

    @Test
    fun `rem refuses a root key`() {
        StepVerifier.create(svc.rem(rootKeys.of(ChatDomain.USER))).verifyError(RootKeyDeletionException::class.java)
    }

    @Test
    fun `a removed key has no root`() {
        val key = svc.key(ChatDomain.USER).block()!!
        svc.rem(key).block()
        StepVerifier.create(svc.rootOf(key.id)).verifyComplete()
    }

    @Test
    fun `a root key returns itself as its root`() {
        val root = rootKeys.of(ChatDomain.MESSAGE)
        Assertions.assertThat(svc.rootOf(root.id).block()).isEqualTo(root.id)
    }

    @Test
    fun `created key should Exist`() {
        val keyStream = svc
            .key(ChatDomain.USER)
            .flatMap(svc::exists)

        StepVerifier
            .create(keyStream)
            .assertNext {
                Assertions
                    .assertThat(it)
                    .isTrue()
            }
            .verifyComplete()
    }

    @Test
    fun `should create an key`() {
        val key = svc.key(ChatDomain.USER)

        StepVerifier
            .create(key)
            .assertNext {
                Assertions
                    .assertThat(it)
                    .hasNoNullFieldsOrProperties()
                    .hasFieldOrProperty("id")
            }
            .verifyComplete()
    }

    @Test
    fun `should delete a key`() {
        val key = svc.key(ChatDomain.USER)
        val deleteStream = Flux
            .from(key)
            .flatMap(svc::rem)

        StepVerifier
            .create(deleteStream)
            .expectSubscription()
            .verifyComplete()
    }
}