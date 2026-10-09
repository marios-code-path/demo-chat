package com.demo.chat.test.controller.webflux.composite

import com.demo.chat.controller.webflux.ChatMessageServiceController
import com.demo.chat.controller.webflux.resolve.ResolvedKeyArgumentResolver
import com.demo.chat.domain.LongUtil
import com.demo.chat.domain.Message
import com.demo.chat.domain.SimpleMessageKey
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.UUIDUtil
import com.demo.chat.domain.Key
import com.demo.chat.domain.command.*
import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.core.KeyVerifier
import com.demo.chat.test.anyObject
import com.demo.chat.test.config.TestCompositeServiceBeans
import com.demo.chat.test.key.FakeKeyServices
import com.demo.chat.test.key.TestVerifiers
import org.junit.jupiter.api.Assertions.assertEquals
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.support.StaticListableBeanFactory
import org.springframework.http.MediaType
import org.springframework.http.codec.json.JacksonJsonEncoder
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import tools.jackson.databind.json.JsonMapper
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

/** These HTTP fixtures check serialization. Existing security tests check authorization separately. */
internal class MessagingRestFixtures<T>(
    val kind: String,
    private val mapper: JsonMapper,
    roots: RootKeys<T>,
    typeUtil: TypeUtil<T>,
    id: T,
    root: T,
    sender: T,
    private val room: T,
) {
    private val key = SimpleMessageKey(id, root, sender, room, Instant.parse("2026-10-09T12:00:00Z"))
    private val receipt = Receipt("c-rest", key)
    private val message = Message.create(key, "hello λ", true)
    private val backends = mapOf(
        BackendId.PERSISTENCE to BackendStatus(BackendState.PENDING),
        BackendId.INDEX to BackendStatus(BackendState.SUCCEEDED, 1),
        BackendId.VECTOR to BackendStatus(
            BackendState.UNCERTAIN, 5, "private reason", Instant.parse("2026-10-09T12:00:30Z"),
        ),
        BackendId.PUBSUB to BackendStatus(BackendState.FAILED, 1, "private refusal"),
    )
    private val beans = TestCompositeServiceBeans<T, String>()
    private val verifier = TestVerifiers.holding(roots, listOf(
        Key.of(room, roots.of(ChatDomain.MESSAGE_TOPIC).id),
        Key.of(id, roots.of(ChatDomain.MESSAGE).id),
    ))
    private val resolver = ResolvedKeyArgumentResolver(
        StaticListableBeanFactory(mapOf("verifier" to verifier)).getBeanProvider(KeyVerifier::class.java),
        StaticListableBeanFactory(mapOf("typeUtil" to typeUtil)).getBeanProvider(TypeUtil::class.java),
    )
    private val client = WebTestClient.bindToController(ChatMessageServiceController(beans))
        .argumentResolvers { it.addCustomResolver(resolver) }
        .httpMessageCodecs { it.defaultCodecs().jacksonJsonEncoder(JacksonJsonEncoder(mapper)) }
        .build()

    private fun fixture(shape: String): String {
        val directory = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .map { it.resolve("chat-mcp/src/test/resources/messaging") }
            .first { Files.isDirectory(it) }
        return Files.readString(directory.resolve("$kind-$shape"))
    }

    private fun compare(shape: String, body: String) =
        assertEquals(mapper.readTree(fixture(shape)), mapper.readTree(body), "$kind $shape")

    fun checkSendAndStatus() {
        for (outcome in CallerOutcome.entries) {
            val result = MessageSendResult(receipt, outcome, if (outcome == CallerOutcome.ACCEPTED) emptyMap() else backends)
            given(beans.messageService().submit(anyObject())).willReturn(Mono.just(result))
            val response = client.post().uri("/message/submit/$room")
                .contentType(MediaType.TEXT_PLAIN).header("Idempotency-Key", "request:1")
                .bodyValue("hello λ").exchange()
                .expectStatus().isEqualTo(when (outcome) {
                    CallerOutcome.COMPLETED -> 201
                    CallerOutcome.INCOMPLETE -> 424
                    else -> 202
                }).expectBody(String::class.java).returnResult().responseBody!!
            compare("send-${outcome.name.lowercase()}.json", response)
            assertEquals(
                mapper.readTree(fixture("receipt.json")), mapper.readTree(response).get("receipt"),
            )
        }
        given(beans.messageService().commandStatus(anyObject())).willReturn(Mono.just(
            CommandStatus("c-rest", key.from, "request:1", receipt, backends, 2147483648L),
        ))
        val response = client.get().uri("/message/command/c-rest").exchange()
            .expectStatus().isOk.expectBody(String::class.java).returnResult().responseBody!!
        compare("status.json", response)
    }

    fun checkHistory() {
        given(beans.messageService().listMessages(anyObject())).willReturn(Flux.just(message, message))
        val body = client.get().uri("/message/list/$room").exchange()
            .expectStatus().isOk.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_NDJSON)
            .expectBody(String::class.java).returnResult().responseBody!!
        assertEquals(
            fixture("history.ndjson").lines().filter(String::isNotBlank).map(mapper::readTree),
            body.lines().filter(String::isNotBlank).map(mapper::readTree),
        )
    }

    fun checkMessage() {
        given(beans.messageService().messageById(anyObject())).willReturn(Mono.just(message))
        val body = client.get().uri("/message/id/${key.id}").exchange()
            .expectStatus().isOk.expectBody(String::class.java).returnResult().responseBody!!
        compare("message.json", body)
    }

    companion object {
        fun both(mapper: JsonMapper): List<MessagingRestFixtures<*>> = listOf(
            MessagingRestFixtures("long", mapper, FakeKeyServices.longRoots(), LongUtil(),
                1554361326074068992L, 1554361143634427905L, 7L, 12345L),
            MessagingRestFixtures("uuid", mapper, FakeKeyServices.uuidRoots(), UUIDUtil(),
                UUID.fromString("6f1e0b3a-2c4d-4e5f-8a9b-0c1d2e3f4a5b"),
                UUID.fromString("00000000-0000-4000-8000-00000000c0de"),
                UUID.fromString("00000000-0000-4000-8000-000000000007"),
                UUID.fromString("00000000-0000-4000-8000-000000003039")),
        )
    }
}
