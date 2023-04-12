package com.demo.chat.deploy.authserv

import com.demo.chat.config.deploy.authserv.Oauth2ClientProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["com.demo.chat.config", "com.demo.chat.config.client.discovery"])
@EnableConfigurationProperties(Oauth2ClientProperties::class)
class AuthServiceApp {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<AuthServiceApp>(*args)
        }
    }
}