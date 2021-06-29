package com.demo.chat.secure

import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder


class ChatSecurityApp {
    companion object {
        val am = SampleAuthenticationManager()

        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<ChatSecurityApp>(*args)
        }
    }

    @Bean
    fun runsTheSecurity() {
        println("username: ")
        val username = readLine()
        println("password: ")
        val password = readLine()

        try {
            val request = UsernamePasswordAuthenticationToken(username, password)
            val result = am.authenticate(request)
            SecurityContextHolder.getContext().authentication = result
        } catch (e: AuthenticationException) {
            println("Authentication failed :" + e.message)
        }
        println("Success : ${SecurityContextHolder.getContext().authentication}")
    }
}

class SampleAuthenticationManager : AuthenticationManager {
    override fun authenticate(auth: Authentication): Authentication {
        if (auth.name.equals(auth.credentials)) {
            val userDetails = ChatUserDetails(
                User.create(Key.funKey(1), auth.name, auth.name, "http://foo"),
                listOf("ROLE_USER")
            )
            return UsernamePasswordAuthenticationToken( "FOO", auth.credentials, userDetails.authorities)
        }
        throw BadCredentialsException("Bad Credentials")
    }
}