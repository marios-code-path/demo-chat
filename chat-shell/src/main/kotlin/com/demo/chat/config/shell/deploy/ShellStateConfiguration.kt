package com.demo.chat.config.shell.deploy

import com.demo.chat.client.rsocket.EmptyRequestMetadata
import com.demo.chat.client.rsocket.RequestMetadata
import com.demo.chat.client.rsocket.SimpleRequestMetadata
import com.demo.chat.domain.knownkey.Anon
import io.rsocket.metadata.WellKnownMimeType
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.rsocket.metadata.UsernamePasswordMetadata
import org.springframework.util.MimeTypeUtils
import reactor.core.publisher.Sinks.Empty
import java.util.*
import java.util.function.Supplier

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
        println("SUPPLIER SUPPLIES")
        val metadata =
            loginMetadata
                .map<RequestMetadata> { SimpleRequestMetadata(UsernamePasswordMetadata(it.username, it.password),
                    MimeTypeUtils.parseMimeType(WellKnownMimeType.MESSAGE_RSOCKET_AUTHENTICATION.string)) }
                .orElseGet { EmptyRequestMetadata }

        metadata
    }
}