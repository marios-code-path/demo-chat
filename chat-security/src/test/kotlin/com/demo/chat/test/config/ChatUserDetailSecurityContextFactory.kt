package com.demo.chat.test.config

import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.security.ChatUserDetails
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.test.context.support.WithSecurityContext
import org.springframework.security.test.context.support.WithSecurityContextFactory

@Retention(AnnotationRetention.RUNTIME)
@WithSecurityContext(factory = ChatUserDetailSecurityContextFactory::class)
annotation class WithLongCustomChatUser(val userId: Long, val roles: Array<String>)


class ChatUserDetailSecurityContextFactory : WithSecurityContextFactory<WithLongCustomChatUser> {
    override fun createSecurityContext(annotation: WithLongCustomChatUser): SecurityContext {
        val ctx: SecurityContext = SecurityContextHolder.createEmptyContext()

        val principal = ChatUserDetails(
            User.create(Key.funKey(annotation.userId), "TestUser", "TestHandle", "http://test"),
            annotation.roles.asList()
        )

        val auth = UsernamePasswordAuthenticationToken(principal, "password", principal.authorities)

        ctx.authentication = auth

        return ctx
    }
}