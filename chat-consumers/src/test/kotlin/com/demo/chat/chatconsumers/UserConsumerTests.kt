package com.demo.chat.chatconsumers

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.test.StepVerifier

@ExtendWith(SpringExtension::class)
@SpringBootTest(classes = [ChatConsumersApplication::class])
@Disabled
class UserConsumerTests {

    val logger = LoggerFactory.getLogger(this::class.simpleName)

    @Autowired
    private lateinit var userClient: ChatUserClient

    private var testName: String = "Ferris The Dog"
    private var testHandle: String = "meatman"
    private var testImageUri: String = "http://pivotal.io/big.gif"

    @Test
    fun `should create a user`() {
        val p = userClient.callCreateUser(testName, testHandle, testImageUri)

        StepVerifier
                .create(p)
                .expectSubscription()
                .verifyComplete()
    }

    @Test
    fun `should return a user by handle`() {
        val pub = userClient.callGetUserByHandle(testHandle)

        StepVerifier
                .create(pub)
                .expectSubscription()
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .isNotNull
                            .hasNoNullFieldsOrProperties()
                }
                .verifyComplete()
    }


    @Test
    fun `should return a user by handle then by ID`() {
        val pub = userClient
                .callGetUserByHandle(testHandle)
                .flatMap {
                    userClient
                            .callGetUserById(it.key.id)
                }

        StepVerifier
                .create(pub)
                .expectSubscription()
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .isNotNull
                            .hasNoNullFieldsOrProperties()
                }
                .verifyComplete()
    }
}
