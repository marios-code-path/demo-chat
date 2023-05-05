package com.demo.chat.deploy.authserv

import com.demo.chat.config.deploy.authserv.Oauth2ClientProperties
import com.demo.chat.domain.TopicMember
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.boot.web.servlet.server.ServletWebServerFactory
import org.springframework.context.annotation.ImportRuntimeHints
import org.springframework.lang.Nullable
import org.springframework.stereotype.Component
import org.springframework.web.servlet.config.annotation.EnableWebMvc

@SpringBootApplication(scanBasePackages = ["com.demo.chat.config", "com.demo.chat.config.client.discovery"])
@EnableConfigurationProperties(Oauth2ClientProperties::class)
@ImportRuntimeHints(HttpServletRuntimeHints::class)
@EnableWebMvc
class AuthServiceApp

fun main(args: Array<String>) {
    runApplication<AuthServiceApp>(*args)
}

@Component
class HttpServletRuntimeHints : RuntimeHintsRegistrar {

    override fun registerHints(hints: RuntimeHints, classLoader: ClassLoader?) {
        val proxies = hints.proxies()
        proxies.registerJdkProxy(HttpServlet::class.java)
        proxies.registerJdkProxy(HttpServletRequest::class.java)
        proxies.registerJdkProxy(HttpServletResponse::class.java)
        proxies.registerJdkProxy(TopicMember::class.java)
    }

}