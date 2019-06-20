package com.demo.chat.test

import com.demo.chat.*
import com.demo.chat.domain.User
import com.demo.chat.domain.UserKey
import com.demo.chat.service.ChatUserService
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
import org.springframework.boot.test.context.SpringBootTest
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
@Import(RSocketTestConfig::class)
class RSocketUserTests {
    val log = LoggerFactory.getLogger(this::class.simpleName)

    @Autowired
    private lateinit var builder: RSocketRequester.Builder

    private lateinit var socket: RSocket
    private lateinit var requestor: RSocketRequester

    @Autowired
    private lateinit var userService: ChatUserService<out User, UserKey>
    val defaultImgUri = "http://"
    val randomHandle = randomAlphaNumeric(4)
    val randomName = randomAlphaNumeric(6)
    val randomUserId = UUID.randomUUID()!!
    val randomUser = TestChatUser(TestChatUserKey(randomUserId, randomHandle), randomName, defaultImgUri, Instant.now())

    @BeforeEach
    fun setUp(@Autowired config: RSocketTestConfig) {
        config.rSocketInit()

        requestor = builder.connect(TcpClientTransport.create(7070)).block()!!
        socket = requestor.rsocket()
    }

    @AfterEach
    fun tearDown(@Autowired config: RSocketTestConfig) {
        config.rSocketComplete()
    }

    @Test
    fun `should call user create`() {
        BDDMockito.given(userService.createUser(anyObject(), anyObject(), anyObject()))
                .willReturn(Mono.just(randomUser))

        StepVerifier
                .create(
                        requestor
                                .route("user-create")
                                .data(UserCreateRequest(randomName, randomHandle, defaultImgUri))
                                .retrieveMono(TestChatUser::class.java)
                )
                .expectSubscription()
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .isNotNull
                            .hasNoNullFieldsOrProperties()

                    Assertions.assertThat(it.key)
                            .isNotNull
                            .hasNoNullFieldsOrProperties()
                            .hasFieldOrPropertyWithValue("handle", randomHandle)
                }
                .verifyComplete()
    }


    @Test
    fun `should get a user by handle`() {
        BDDMockito.given(userService.getUser(anyObject()))
                .willReturn(Mono.just(randomUser))

        StepVerifier
                .create(
                        requestor
                                .route("user-handle")
                                .data(UserRequest(randomHandle))
                                .retrieveMono(TestChatUser::class.java)
                )
                .expectSubscription()
                .assertNext {
                    Assertions
                            .assertThat(it.key)
                            .isNotNull
                            .hasNoNullFieldsOrProperties()
                            .hasFieldOrPropertyWithValue("handle", randomHandle)
                            .hasFieldOrPropertyWithValue("userId", randomUserId)
                }
                .verifyComplete()
    }

    @Test
    fun `should list users`() {
        BDDMockito.given(userService.getUsersById(anyObject()))
                .willReturn(Flux.just(randomUser))

        StepVerifier.create(requestor
                .route("user-msgId-list")
                .data(Flux.just(UserRequestId(randomUserId)), UserRequestId::class.java)
                .retrieveFlux(TestChatUser::class.java)
        )
                .expectSubscription()
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .isNotNull
                            .hasNoNullFieldsOrProperties()

                    Assertions
                            .assertThat(it.key)
                            .isNotNull
                            .hasNoNullFieldsOrProperties()
                            .hasFieldOrPropertyWithValue("handle", randomHandle)
                            .hasFieldOrPropertyWithValue("userId", randomUserId)
                }
                .verifyComplete()
    }
}