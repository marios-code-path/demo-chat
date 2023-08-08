package com.demo.chat.shell

import com.demo.chat.client.rsocket.RequestMetadata
import com.demo.chat.domain.knownkey.Anon
import io.rsocket.metadata.WellKnownMimeType
import jakarta.annotation.PreDestroy
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.rsocket.EnableRSocketSecurity
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.rsocket.metadata.UsernamePasswordMetadata
import org.springframework.shell.ExitRequest
import org.springframework.shell.command.CommandHandlingResult
import org.springframework.shell.command.annotation.ExceptionResolver
import org.springframework.util.MimeTypeUtils
import org.springframework.web.reactive.config.EnableWebFlux
import reactor.core.publisher.Hooks
import java.util.*
import java.util.function.Supplier

@SpringBootApplication(scanBasePackages = ["com.demo.chat.config", "com.demo.chat.shell"])
@EnableRSocketSecurity
@EnableWebFlux
class ShellApp {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            Hooks.onOperatorDebug()
            runApplication<ShellApp>(*args)
        }
    }
}

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