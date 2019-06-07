package com.demo.chat

import com.demo.chat.domain.*
import com.demo.chat.repository.cassandra.ChatUserHandleRepository
import com.demo.chat.repository.cassandra.ChatUserRepository
import com.demo.chat.service.ChatUserService
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.rsocket.RSocket
import io.rsocket.transport.netty.client.TcpClientTransport
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.rsocket.server.RSocketServerBootstrap
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant
import java.util.*

@SpringBootTest(classes = [ChatServiceRsocketApplication::class])
@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(TestSetupConfig::class)
class ChatRSocketUserTests {
    val log = LoggerFactory.getLogger(this::class.simpleName)

    @Autowired
    private lateinit var builder: RSocketRequester.Builder

    private lateinit var socket: RSocket
    private lateinit var requestor: RSocketRequester

    @Autowired
    private  lateinit var rsboot: RSocketServerBootstrap

    @Autowired
    private lateinit var userService: ChatUserService<ChatUser, UserKey>

    val randomHandle = randomAlphaNumeric(4)
    val randomName = randomAlphaNumeric(6)
    val randomUserId = UUID.randomUUID()!!

    @BeforeEach
    fun setUp() {
        when(rsboot.isRunning) {
            false -> {
                log.warn("RSocket Service is not already running");
                rsboot.start()
            }
            else -> log.warn("RSocket Service is already running")
        }

        requestor = builder.connect(TcpClientTransport.create(7070)).block()!!
        socket = requestor.rsocket()

        val user = ChatUser(ChatUserKey(randomUserId, randomHandle), randomName, Instant.now())

        BDDMockito.given(userService.createUser(anyObject(), anyObject()))
                .willReturn(Mono.just(user))

        BDDMockito.given(userService.getUser(anyObject()))
                .willReturn(Mono.just(user))

        BDDMockito.given(userService.getUsersById(anyObject()))
                .willReturn(Flux.just(user))
    }

    @AfterEach
    fun tearDown() {

    }

    @Test
    fun `should call user create`() {
        StepVerifier
                .create(
                        requestor
                                .route("user-create")
                                .data(UserCreateRequest(randomName, randomHandle))
                                .retrieveMono(TestUserCreateResponse::class.java)
                )
                .expectSubscription()
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .isNotNull

                    Assertions
                            .assertThat(it.user)
                            .isNotNull
                            .hasNoNullFieldsOrProperties()

                    Assertions.assertThat(it.user.key)
                            .isNotNull
                            .hasNoNullFieldsOrProperties()
                            .hasFieldOrPropertyWithValue("handle", randomHandle)
                }
                .verifyComplete()
    }


    @Test
    fun `should get a user by handle`() {

        StepVerifier
                .create(
                        requestor
                                .route("user-handle")
                                .data(TestUserRequest(randomHandle))
                                .retrieveMono(TestUserResponse::class.java)
                )
                .expectSubscription()
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .isNotNull

                    Assertions
                            .assertThat(it.user.key)
                            .isNotNull
                            .hasNoNullFieldsOrProperties()
                            .hasFieldOrPropertyWithValue("handle", randomHandle)
                            .hasFieldOrPropertyWithValue("userId", randomUserId)
                }
                .verifyComplete()
    }

    @Test
    fun `should list users`() {
        StepVerifier.create(requestor
                .route("user-id-list")
                .data(Flux.just(UserRequestId(randomUserId)), UserRequestId::class.java)
                .retrieveFlux(TestUserResponse::class.java)
        )
                .expectSubscription()
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .isNotNull
                            .hasNoNullFieldsOrProperties()

                    Assertions
                            .assertThat(it.user)
                            .isNotNull
                            .hasNoNullFieldsOrProperties()

                    Assertions
                            .assertThat(it.user.key)
                            .isNotNull
                            .hasNoNullFieldsOrProperties()
                            .hasFieldOrPropertyWithValue("handle", randomHandle)
                            .hasFieldOrPropertyWithValue("userId", randomUserId)
                }
                .verifyComplete()
    }
}