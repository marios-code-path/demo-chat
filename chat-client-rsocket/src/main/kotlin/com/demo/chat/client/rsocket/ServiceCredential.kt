package com.demo.chat.client.rsocket

import io.rsocket.metadata.WellKnownMimeType
import org.springframework.security.rsocket.metadata.UsernamePasswordMetadata
import org.springframework.util.MimeTypeUtils
import java.util.function.Supplier

/**
 * The credential of a service that calls the core RSocket routes.
 *
 * **The core routes require `ROLE_SERVICE` or `ROLE_ADMIN`.** A service such as
 * the authorization server reads the secrets store and the indexes through
 * them, so it presents this credential on every request. See `CHAT-rdlghoqe`.
 *
 * The credential travels as request metadata, as the shell login does. The
 * server judges it on each request.
 */
object ServiceCredential {

    private val mimeType = MimeTypeUtils.parseMimeType(WellKnownMimeType.MESSAGE_RSOCKET_AUTHENTICATION.string)

    /** A blank value is refused here, so a launch fails at startup and not at the first request. */
    fun metadataProvider(username: String, password: String): Supplier<RequestMetadata> {
        require(username.isNotBlank()) { "app.client.rsocket.credential.username is blank." }
        require(password.isNotBlank()) {
            "app.client.rsocket.credential.password is blank. The service account '$username' needs its password."
        }
        val metadata = SimpleRequestMetadata(UsernamePasswordMetadata(username, password), mimeType)
        return Supplier { metadata }
    }
}
