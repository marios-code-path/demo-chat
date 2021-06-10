package com.demo.chat.test

import com.demo.chat.UserCreateRequest
import com.demo.chat.controller.edge.UserRestController
import org.junit.Test
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.reactive.server.WebTestClient

@WebFluxTest(controllers = [UserRestController::class])
class ServiceTests {
    private lateinit var testClient: WebTestClient

    @Test(expected = Exception::class)
    @WithMockUser(username = "chatadmin", roles = ["chat_admin"])
    fun addUser() {
        testClient
            .put()
            .uri("/add")
            .bodyValue(UserCreateRequest("test", "handle", "http://"))
            .exchange()
            .expectStatus()
            .isOk
    }
}