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
import reactor.core.Disposable
import reactor.core.publisher.Sinks.Empty
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Supplier

@Configuration
class ShellStateConfiguration {

    @Value("\${app.shell.anonymous.password:_}")
    private lateinit var anonymousPassword: String

    companion object {
        var loggedInUser: Optional<Any> = Optional.empty()
        var loginMetadata: Optional<UsernamePasswordMetadata> = Optional.empty()
        val listeners: MutableMap<String, Disposable> = ConcurrentHashMap()

        /**
         * Forgets the credential and the identity in one step.
         *
         * A failed login must leave neither behind. The two values are one
         * state, because the metadata is what the server judges and the
         * identity is what the commands read. A caller that never logged in
         * keeps the `Anon` floor of [CommandsUtil.identity].
         */
        fun clearLogin() {
            loggedInUser = Optional.empty()
            loginMetadata = Optional.empty()
        }
    }

    @Bean
    fun requestMetadataProvider(): Supplier<RequestMetadata> = Supplier {
        val metadata =
            loginMetadata
                .map<RequestMetadata> { SimpleRequestMetadata(UsernamePasswordMetadata(it.username, it.password),
                    MimeTypeUtils.parseMimeType(WellKnownMimeType.MESSAGE_RSOCKET_AUTHENTICATION.string)) }
                .orElseGet { EmptyRequestMetadata }

        metadata
    }
}