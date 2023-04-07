package com.demo.chat.deploy.authserv

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity

@SpringBootApplication(scanBasePackages = ["com.demo.chat.config"])
open class AuthServiceApp {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<AuthServiceApp>(*args)
        }
    }
}