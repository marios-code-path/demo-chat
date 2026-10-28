package com.demo.chat.test.rsocket

import com.demo.chat.domain.ByIdRequest
import com.demo.chat.domain.ByStringRequest
import com.demo.chat.domain.RegisterCollisionRequest
import com.demo.chat.domain.RegisterCollisionResult
import com.demo.chat.domain.RegisterCollisionStatus
import com.demo.chat.domain.RegisterOwnerResult
import com.demo.chat.domain.RegisterOwnerStatus
import com.demo.chat.domain.RegisterRoomResult
import com.demo.chat.domain.RegisterRoomStatus
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(RegisterMetadataTransportTests.ProbeConfiguration::class)
class RegisterMetadataTransportTests : RSocketTestBase() {

    @Test
    fun `retrieveMono decodes every collision result`() {
        val cases = listOf(
            "clear" to RegisterCollisionResult(RegisterCollisionStatus.CLEAR, null, null),
            "room" to RegisterCollisionResult(RegisterCollisionStatus.ROOM_HELD, "demo-chat-fr-a", null),
            "register" to RegisterCollisionResult(RegisterCollisionStatus.REGISTER_HELD, null, "demo"),
        )

        cases.forEach { (name, expected) ->
            StepVerifier.create(
                requester.route("register-probe.collision")
                    .data(RegisterCollisionRequest(name, emptySet()))
                    .retrieveMono(RegisterCollisionResult::class.java)
            ).expectNext(expected).verifyComplete()
        }
    }

    @Test
    fun `allowedNames crosses the collision request transport`() {
        StepVerifier.create(
            requester.route("register-probe.collision")
                .data(RegisterCollisionRequest("room", setOf("demo-chat-fr-a")))
                .retrieveMono(RegisterCollisionResult::class.java)
        ).expectNext(RegisterCollisionResult(RegisterCollisionStatus.CLEAR, null, null))
            .verifyComplete()
    }

    @Test
    fun `retrieveMono decodes every room result`() {
        val cases = listOf(
            "found" to RegisterRoomResult(RegisterRoomStatus.FOUND, "room-1"),
            "missing" to RegisterRoomResult(RegisterRoomStatus.NOT_FOUND, null),
            "ambiguous" to RegisterRoomResult(RegisterRoomStatus.AMBIGUOUS_NAME, null),
        )

        cases.forEach { (name, expected) ->
            StepVerifier.create(
                requester.route("register-probe.room")
                    .data(ByStringRequest(name))
                    .retrieveMono(RegisterRoomResult::class.java)
            ).expectNext(expected).verifyComplete()
        }
    }

    @Test
    fun `retrieveMono decodes every owner result`() {
        val cases = listOf(
            "found" to RegisterOwnerResult(RegisterOwnerStatus.FOUND, "user-1", "Mario"),
            "missing" to RegisterOwnerResult(RegisterOwnerStatus.NO_OWNER, null, null),
            "multiple" to RegisterOwnerResult(RegisterOwnerStatus.MULTIPLE_OWNERS, null, null),
        )

        cases.forEach { (id, expected) ->
            StepVerifier.create(
                requester.route("register-probe.owner")
                    .data(ByIdRequest(id))
                    .retrieveMono(RegisterOwnerResult::class.java)
            ).expectNext(expected).verifyComplete()
        }
    }

    @TestConfiguration
    class ProbeConfiguration {
        @Bean
        fun registerProbeController() = RegisterProbeController()
    }

    @Controller
    class RegisterProbeController {
        @MessageMapping("register-probe.collision")
        fun collision(request: RegisterCollisionRequest): Mono<RegisterCollisionResult> = Mono.just(
            when (request.name) {
                "clear" -> RegisterCollisionResult(RegisterCollisionStatus.CLEAR, null, null)
                "room" -> if ("demo-chat-fr-a" in request.allowedNames) {
                    RegisterCollisionResult(RegisterCollisionStatus.CLEAR, null, null)
                } else {
                    RegisterCollisionResult(RegisterCollisionStatus.ROOM_HELD, "demo-chat-fr-a", null)
                }
                else -> RegisterCollisionResult(RegisterCollisionStatus.REGISTER_HELD, null, "demo")
            }
        )

        @MessageMapping("register-probe.room")
        fun room(request: ByStringRequest): Mono<RegisterRoomResult> = Mono.just(
            when (request.name) {
                "found" -> RegisterRoomResult(RegisterRoomStatus.FOUND, "room-1")
                "missing" -> RegisterRoomResult(RegisterRoomStatus.NOT_FOUND, null)
                else -> RegisterRoomResult(RegisterRoomStatus.AMBIGUOUS_NAME, null)
            }
        )

        @MessageMapping("register-probe.owner")
        fun owner(request: ByIdRequest<String>): Mono<RegisterOwnerResult> = Mono.just(
            when (request.id) {
                "found" -> RegisterOwnerResult(RegisterOwnerStatus.FOUND, "user-1", "Mario")
                "missing" -> RegisterOwnerResult(RegisterOwnerStatus.NO_OWNER, null, null)
                else -> RegisterOwnerResult(RegisterOwnerStatus.MULTIPLE_OWNERS, null, null)
            }
        )
    }
}
