package com.demo.chat.test

import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.secure.ChatUserDetails
import com.demo.chat.secure.ChatUserDetailsService
import com.demo.chat.service.AuthorizationMeta
import com.demo.chat.test.index.MockIndexResolver
import com.demo.chat.test.index.MockIndexSupplier
import com.demo.chat.test.persistence.MockPersistenceResolver
import com.demo.chat.test.persistence.MockPersistenceSupplier
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
@ExtendWith(
    SpringExtension::class,
    MockPersistenceResolver::class,
    MockIndexResolver::class
)
class UserDetailsTests {

    val details = ChatUserDetails(
        User.create(Key.funKey(11), "NAME", "HANDLE", "HTTP://TEST"),
        listOf(AuthorizationMeta(11, 11, "EXEC"))
    )


    @Test
    fun `should find`() {
    }

    @Test
    fun `should update password`() {

    }

    @TestConfiguration
    class Configuration {
        class TestUserDetailService : ChatUserDetailsService<Int, Map<String, String>>(
            MockPersistenceSupplier().get(),
            MockIndexSupplier().get(),
            
        )
    }
}