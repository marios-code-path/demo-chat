package com.demo.chat.authserv

import com.demo.chat.deploy.AppConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity

@SpringBootApplication
@EnableWebSecurity
@Import(AppConfiguration::class)
class ChatAuthorizationServerApplication

fun main(args: Array<String>) {
    runApplication<ChatAuthorizationServerApplication>(*args)
}
