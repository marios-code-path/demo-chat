package com.demo.chat.test.index

import com.demo.chat.domain.ChatException
import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyValuePair
import com.demo.chat.index.cassandra.domain.ChatKeyValueIndexById
import com.demo.chat.index.cassandra.domain.ChatKeyValueIndexByIdKey
import com.demo.chat.index.cassandra.impl.KeyValueIndex
import com.demo.chat.index.cassandra.repository.KeyValueIndexByIdRepository
import com.demo.chat.index.cassandra.repository.KeyValueIndexRepository
import com.demo.chat.service.core.KeyValueIndexFields
import com.demo.chat.service.core.KeyValueIndexFieldsEntry
import com.demo.chat.service.core.KeyValueIndexService
import com.demo.chat.service.core.TypedKeyValueIndexFields
import com.demo.chat.test.anyObject
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.UUID

data class IndexedClient(val clientId: String, val size: Int)

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(SpringExtension::class)
class KeyValueIndexTests {

    @MockBean
    lateinit var byFieldRepo: KeyValueIndexRepository<UUID>

    @MockBean
    lateinit var byIdRepo: KeyValueIndexByIdRepository<UUID>

    private val entityId: UUID = UUID.randomUUID()

    private val fields = TypedKeyValueIndexFields(
        listOf(
            KeyValueIndexFieldsEntry(
                IndexedClient::class.java,
                KeyValueIndexFields { value ->
                    val client = value as IndexedClient
                    listOf(Pair("client_id", client.clientId), Pair("size", client.size.toString()))
                }
            )
        )
    )

    private lateinit var index: KeyValueIndexService<UUID, Map<String, String>>

    private fun pair(value: Any) = KeyValuePair.create(Key.funKey(entityId), value)

    @BeforeEach
    fun setUp() {
        index = KeyValueIndex(fields, byFieldRepo, byIdRepo)
    }

    @Test
    fun `add writes one row per field to both tables`() {
        BDDMockito.given(byFieldRepo.save(anyObject())).willReturn(Mono.empty())
        BDDMockito.given(byIdRepo.save(anyObject())).willReturn(Mono.empty())

        StepVerifier
            .create(index.add(pair(IndexedClient("abc", 7))))
            .verifyComplete()

        BDDMockito.verify(byFieldRepo, BDDMockito.times(2)).save(anyObject())
        BDDMockito.verify(byIdRepo, BDDMockito.times(2)).save(anyObject())
    }

    @Test
    fun `an unregistered value type fails the add signal`() {
        StepVerifier
            .create(index.add(pair("a string value")))
            .verifyError(ChatException::class.java)
    }

    @Test
    fun `rem deletes every row the by id table holds`() {
        BDDMockito.given(byIdRepo.findByKeyId(entityId)).willReturn(
            Flux.just(
                ChatKeyValueIndexById(ChatKeyValueIndexByIdKey(entityId, "client_id", "abc")),
                ChatKeyValueIndexById(ChatKeyValueIndexByIdKey(entityId, "size", "7")),
            )
        )
        BDDMockito.given(byFieldRepo.delete(anyObject())).willReturn(Mono.empty())
        BDDMockito.given(byIdRepo.delete(anyObject())).willReturn(Mono.empty())

        StepVerifier
            .create(index.rem(Key.funKey(entityId)))
            .verifyComplete()

        BDDMockito.verify(byFieldRepo, BDDMockito.times(2)).delete(anyObject())
        BDDMockito.verify(byIdRepo, BDDMockito.times(2)).delete(anyObject())
    }

    @Test
    fun `findBy returns the key of every matching row`() {
        BDDMockito.given(byFieldRepo.findByKeyFieldAndKeyValue("client_id", "abc")).willReturn(
            Flux.just(
                com.demo.chat.index.cassandra.domain.ChatKeyValueIndex(
                    com.demo.chat.index.cassandra.domain.ChatKeyValueIndexKey("client_id", "abc", entityId)
                )
            )
        )

        StepVerifier
            .create(index.findBy(mapOf("client_id" to "abc")))
            .assertNext { key -> Assertions.assertThat(key.id).isEqualTo(entityId) }
            .verifyComplete()
    }

    @Test
    fun `an empty query returns no key`() {
        StepVerifier
            .create(index.findBy(emptyMap()))
            .verifyComplete()
    }
}
