package com.demo.chat.test.index

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

class UserIndexTests {
//
//    @Test
//    fun `should get a user by handle`() {
//        BDDMockito.given(userPersistence.getByHandle(com.demo.chat.test.domain.anyObject()))
//                .willReturn(Mono.just(randomUser))
//
//        StepVerifier
//                .create(
//                        requestor
//                                .route("user-by-handle")
//                                .data(UserRequest(randomHandle))
//                                .retrieveMono(TestChatUser::class.java)
//                )
//                .expectSubscription()
//                .assertNext {
//                    Assertions
//                            .assertThat(it.key)
//                            .isNotNull
//                            .hasNoNullFieldsOrProperties()
//                            .hasFieldOrPropertyWithValue("handle", randomHandle)
//                            .hasFieldOrPropertyWithValue("id", randomUserId)
//                }
//                .verifyComplete()
//    }

}