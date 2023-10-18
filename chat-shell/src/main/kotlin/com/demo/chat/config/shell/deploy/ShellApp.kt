package com.demo.chat.config.shell.deploy

import com.demo.chat.client.rsocket.RequestMetadata
import com.demo.chat.domain.knownkey.Anon
import io.rsocket.metadata.WellKnownMimeType
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.rsocket.EnableRSocketSecurity
import org.springframework.security.rsocket.metadata.UsernamePasswordMetadata
import org.springframework.util.MimeTypeUtils
import org.springframework.web.reactive.config.EnableWebFlux
import java.util.*
import java.util.function.Supplier

@ComponentScan("com.demo.chat.shell")
@EnableRSocketSecurity
@EnableWebFlux
@Configuration
class ShellApp

@Configuration
class ShellStateConfiguration {

    @Value("\${app.shell.anonymous.password:_}")
    private lateinit var anonymousPassword: String

    companion object {
        var loggedInUser: Optional<Any> = Optional.empty()
        var loginMetadata: Optional<UsernamePasswordMetadata> = Optional.empty()
    }

    @Bean
    fun requestMetadataProvider(): Supplier<RequestMetadata> = Supplier {
        val metadata = RequestMetadata(
            loginMetadata
                .map { UsernamePasswordMetadata(it.username, it.password) }
                .orElseGet { UsernamePasswordMetadata(Anon::class.java.simpleName, anonymousPassword) },
            MimeTypeUtils.parseMimeType(WellKnownMimeType.MESSAGE_RSOCKET_AUTHENTICATION.string)
        )
        metadata
    }
}