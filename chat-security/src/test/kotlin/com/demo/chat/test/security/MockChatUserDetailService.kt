package com.demo.chat.test.security


import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.security.ChatUserDetails
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService

class MockChatUserDetailService<T>(uid: T, roles: Collection<String>) : MapReactiveUserDetailsService(
    ChatUserDetails(
        User.create(Key.funKey(uid), "TestUser", "TestHandle", "http://test"),
        roles
    ).apply {
        password = "password"
    }
)

@TestConfiguration
class LongUserDetailsConfiguration {
    val service = MockChatUserDetailService(1L, listOf("TEST"))
    @Bean
    fun mockChatUserDetailService() = service
}