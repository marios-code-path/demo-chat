package com.demo.chat.test.rsocket.controller.composite

import com.demo.chat.controller.composite.mapping.UserServiceControllerMapping
import com.demo.chat.domain.ByIdRequest
import com.demo.chat.domain.UserCreateRequest
import com.demo.chat.service.composite.impl.UserServiceImpl
import com.demo.chat.domain.Key
import com.demo.chat.service.composite.ChatUserService
import com.demo.chat.service.core.UserIndexService
import com.demo.chat.service.core.UserPersistence
import com.demo.chat.test.TestBase
import com.demo.chat.test.TestChatUser
import com.demo.chat.test.TestChatUserKey
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.messaging.rsocket.retrieveFlux
import org.springframework.messaging.rsocket.retrieveMono
import org.springframework.stereotype.Controller
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant
import java.util.*
import java.util.function.Function

@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(MockCoreServicesConfiguration::class, UserControllerTests.TestConfiguration::class)
open class UserControllerTests : RSocketControllerTestBase() {
    @Autowired
    lateinit var userIndex: UserIndexService<UUID, Map<String, String>>

    @Autowired
    lateinit var userPersistence: UserPersistence<UUID>

    val defaultImgUri = "http://"
    val randomHandle = TestBase.randomAlphaNumeric(4)
    val randomName = TestBase.randomAlphaNumeric(6)
    val randomUserId = UUID.randomUUID()!!
    val randomUser = TestChatUser(TestChatUserKey(randomUserId, randomHandle), randomName, defaultImgUri, Instant.now())

    @Test
    fun `should call user create`() {
        BDDMockito.given(userPersistence.key())
                .willReturn(Mono.just(Key.funKey(UUID.randomUUID())))

        BDDMockito.given(userPersistence.add(TestBase.anyObject()))
                .willReturn(Mono.empty())

        BDDMockito.given(userIndex.add(TestBase.anyObject()))
                .willReturn(Mono.empty())

        StepVerifier
                .create(
                        requester
                                .route("user-add")
                                .data(UserCreateRequest(randomName, randomHandle, defaultImgUri))
                                .retrieveMono(Key::class.java)
                )
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .isNotNull
                            .hasNoNullFieldsOrProperties()
                }
                .verifyComplete()
    }

    @Test
    fun `should find user`() {
        BDDMockito.given(userPersistence.get(TestBase.anyObject()))
                .willReturn(Mono.just(randomUser))

        StepVerifier
                .create(
                        requester
                                .route("user-by-id")
                                .data(ByIdRequest(randomUserId))
                                .retrieveMono<TestChatUser>()
                )
                .expectSubscription()
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .hasNoNullFieldsOrProperties()

                    Assertions.assertThat(it.key)
                            .hasNoNullFieldsOrProperties()
                            .hasFieldOrPropertyWithValue("handle", randomHandle)
                }
                .verifyComplete()
    }


    fun `should list users`() {
        BDDMockito.given(userPersistence.get(TestBase.anyObject()))
                .willReturn(Mono.just(randomUser))

        StepVerifier.create(requester
                .route("user-by-ids")
                .data(ByIdRequest(randomUserId))
                .retrieveFlux<TestChatUser>()
        )
                .expectSubscription()
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .hasNoNullFieldsOrProperties()

                    Assertions
                            .assertThat(it.key)
                            .hasNoNullFieldsOrProperties()
                            .hasFieldOrPropertyWithValue("handle", randomHandle)
                            .hasFieldOrPropertyWithValue("id", randomUserId)
                }
                .verifyComplete()
    }

    @Configuration
    class TestConfiguration {

        @Bean
        fun testUserImpl(
            persistence: UserPersistence<UUID>,
            index: UserIndexService<UUID, Map<String, String>>,
        ) = UserServiceImpl<UUID, Map<String, String>>(persistence,
                index,
                Function { i -> mapOf(Pair(UserIndexService.HANDLE, i.name)) })

        @Controller
        class TestUserServiceController(b: UserServiceImpl<UUID, Map<String, String>>) :
            UserServiceControllerMapping<UUID>, ChatUserService<UUID> by b

    }
}