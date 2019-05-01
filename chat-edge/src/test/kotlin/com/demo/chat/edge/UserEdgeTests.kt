package com.demo.chat.edge

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.test.StepVerifier

@ExtendWith(SpringExtension::class)
class UserEdgeTests {

    private lateinit var userEdge: ChatUserEdge

    @Test
    fun `verify should create user`() {
        val userCreate = userEdge
                .createUser("Mario Gray", "darkbit1001")

        StepVerifier
                .create(userCreate)
                .assertNext { userAssertion(it) }
                .verifyComplete()
    }

    @Test
    fun `verify should get a user`() {
        val getUser = userEdge
                .getUser("darkbit1001")

        StepVerifier
                .create(getUser)
                .assertNext { userAssertion(it) }
                .verifyComplete()
    }


}
