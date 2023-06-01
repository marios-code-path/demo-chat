package com.demo.chat.config.client.rsocket

import com.demo.chat.client.rsocket.transport.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.File

@Configuration
class RSocketClientTransportConfiguration {

    @Value("\${app.rsocket.transport.websocket.enabled:false}")
    private lateinit var websocket: String

    fun websocketEnabled(): Boolean = websocket.toBoolean()

    @Value("\${app.rsocket.transport.security.truststore.path}")
    private lateinit var trustFile: File

    @Value("\${app.rsocket.transport.security.keystore.path}")
    private lateinit var keyFile: File

    @Value("\${app.rsocket.transport.security.keyfile.pass}")
    private lateinit var pass: String

    @Bean
    fun connection(@Value("\${app.rsocket.transport.security.type}") securityType: String): RSocketClientTransportFactory =
        when (securityType) {
            "insecure" -> InsecureConnection(websocketEnabled())
            "unprotected" -> UnprotectedConnection(websocketEnabled())
            "pkcs12" -> PKCS12ClientConnection(trustFile, keyFile, pass, websocketEnabled())
            "jks" -> JKSSecureConnection(trustFile, keyFile, pass, websocketEnabled())
            else -> throw IllegalArgumentException("Unknown security type: $securityType")
        }
}