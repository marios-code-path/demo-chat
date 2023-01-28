package com.demo.chat.test

import com.demo.chat.controller.composite.UserServiceController
import com.demo.chat.domain.User
import com.demo.chat.service.UserIndexService
import com.demo.chat.service.UserPersistence
import com.demo.chat.test.index.MockIndexResolver
import com.demo.chat.test.index.MockIndexSupplier
import com.demo.chat.test.persistence.MockPersistenceResolver
import com.demo.chat.test.persistence.MockPersistenceSupplier
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.stereotype.Controller
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.util.*
import java.util.function.Function

@Disabled
@ExtendWith(
    SpringExtension::class,
    MockPersistenceResolver::class,
    MockIndexResolver::class
)
@Import(EdgeUserRestControllerTests.TestConfiguration::class)
open class EdgeUserRestControllerTests {

    @Configuration
    class TestConfiguration {

        @Controller
        class TestUserController :
            UserServiceController<UUID, Map<String, String>>(
                MockPersistenceSupplier().get<UUID, String>() as UserPersistence<UUID>,
                MockIndexSupplier().get<UUID, User<UUID>, Map<String, String>>() as UserIndexService<UUID, Map<String, String>>,
                Function { i -> mapOf(Pair(UserIndexService.HANDLE, i.handle)) })
    }
}

