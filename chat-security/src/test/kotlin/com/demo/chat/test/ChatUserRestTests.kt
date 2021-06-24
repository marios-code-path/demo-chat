package com.demo.chat.test

import com.demo.chat.UserCreateRequest
import com.demo.chat.controller.edge.UserServiceController
import com.demo.chat.service.UserIndexService
import com.demo.chat.test.index.MockIndexResolver
import com.demo.chat.test.index.MockIndexSupplier
import com.demo.chat.test.persistence.MockPersistenceResolver
import com.demo.chat.test.persistence.MockPersistenceSupplier
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.stereotype.Controller
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.reactive.server.WebTestClient
import java.util.*
import java.util.function.Function

@Disabled
@ExtendWith(
    SpringExtension::class,
    MockPersistenceResolver::class,
    MockIndexResolver::class
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(EdgeUserRestControllerTests.TestConfiguration::class)
open class EdgeUserRestControllerTests {

    @Test
    fun `should call user create`() {
        WebTestClient.bindToController(
            UserServiceController<UUID, Map<String, String>>(
                MockPersistenceSupplier().get(),
                MockIndexSupplier().get(),
                Function { i -> mapOf(Pair(UserIndexService.HANDLE, i.handle)) })
        ).build()
            .put()
            .uri("/add")
            .bodyValue(UserCreateRequest("TEST", "HANDLE", "HTTP://"))
            .exchange()
            .expectStatus()
            .isOk
    }

    @Configuration
    class TestConfiguration {

        @Controller
        class TestUserController :
            UserServiceController<UUID, Map<String, String>>(
                MockPersistenceSupplier().get(),
                MockIndexSupplier().get(),
                Function { i -> mapOf(Pair(UserIndexService.HANDLE, i.handle)) })
    }
}

