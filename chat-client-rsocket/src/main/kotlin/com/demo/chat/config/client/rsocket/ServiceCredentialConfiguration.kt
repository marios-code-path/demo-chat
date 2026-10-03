package com.demo.chat.config.client.rsocket

import com.demo.chat.client.rsocket.RequestMetadata
import com.demo.chat.client.rsocket.ServiceCredential
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.rsocket.messaging.RSocketStrategiesCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.rsocket.metadata.SimpleAuthenticationEncoder
import java.util.function.Supplier

/**
 * The service credential of an RSocket client, when a launch names one.
 *
 * `app.client.rsocket.credential.username` turns it on. The password comes
 * from `app.client.rsocket.credential.password`, or else from the
 * `CHAT_SERVICE_PASSWORD` environment variable. A blank password fails the
 * start. The
 * shell never sets these values, because it sends the credential of its login.
 * See `CHAT-rdlghoqe`.
 */
@Configuration
@ConditionalOnProperty("app.client.rsocket.credential.username")
class ServiceCredentialConfiguration {

    @Bean
    fun serviceCredentialMetadataProvider(
        @Value("\${app.client.rsocket.credential.username}") username: String,
        @Value("\${app.client.rsocket.credential.password:\${CHAT_SERVICE_PASSWORD:}}") password: String,
    ): Supplier<RequestMetadata> = ServiceCredential.metadataProvider(username, password)

    /** The client must encode the credential metadata. */
    @Bean
    fun serviceCredentialStrategiesCustomizer(): RSocketStrategiesCustomizer =
        RSocketStrategiesCustomizer { strategies -> strategies.encoder(SimpleAuthenticationEncoder()) }
}
