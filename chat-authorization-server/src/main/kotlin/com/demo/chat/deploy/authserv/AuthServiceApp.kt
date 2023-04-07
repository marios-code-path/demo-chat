package com.demo.chat.deploy.authserv

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["com.demo.chat.config"])
class AuthServiceApp {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<AuthServiceApp>(*args)
        }
    }
}