package com.demo.chat.config.client.rsocket

import com.demo.chat.client.rsocket.transport.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import java.io.File

@Configuration
class RSocketClientTransportConfiguration(private val env: Environment) {

    @Value("\${app.rsocket.transport.websocket.enabled:false}")
    private lateinit var websocket: String

    fun websocketEnabled(): Boolean = websocket.toBoolean()

    fun up(secType: String, websocket: Boolean): RSocketClientTransportFactory {
        val trustFile = File(env.getProperty("app.rsocket.transport.security.truststore.path"))
        val keyFile = File(env.getProperty("app.rsocket.transport.security.keystore.path"))
        val pass: String = env.getProperty("app.rsocket.transport.security.keyfile.pass", "")
        return when(secType) {
            "pkcs12" -> PKCS12ClientConnection(trustFile, keyFile, pass, websocket)
            "jks" -> JKSSecureConnection(trustFile, keyFile, pass, websocket)
            else -> throw IllegalArgumentException("Unknown security type: $secType")
        }
    }

    @Bean
    fun connection(@Value("\${app.rsocket.transport.security.type}") securityType: String): RSocketClientTransportFactory =
        when (securityType) {
            "insecure" -> InsecureConnection(websocketEnabled())
            "unprotected" -> UnprotectedConnection(websocketEnabled())
            "pkcs12" -> up(securityType, websocketEnabled())
            "jks" -> up(securityType, websocketEnabled())
            else -> throw IllegalArgumentException("Unknown security type: $securityType")
        }
}