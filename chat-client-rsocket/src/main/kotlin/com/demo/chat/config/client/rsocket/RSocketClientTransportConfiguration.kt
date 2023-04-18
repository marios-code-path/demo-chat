package com.demo.chat.config.client.rsocket

import com.demo.chat.client.rsocket.transport.InsecureConnection
import com.demo.chat.client.rsocket.transport.JKSSecureConnection
import com.demo.chat.client.rsocket.transport.UnprotectedConnection
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.File

@Configuration
class RSocketClientTransportConfiguration {

    @Bean
    @ConditionalOnProperty("app.rsocket.transport.unprotected")
    fun unprotectedConnection() = UnprotectedConnection()

    @Bean
    @ConditionalOnProperty("app.rsocket.transport.insecure")
    fun insecureTransport() = InsecureConnection()

    @Bean
    @ConditionalOnProperty("app.rsocket.transport.jks")
    fun jksSecureTransport(
        @Value("\${app.rsocket.transport.secure.truststore.path}") trustFile: File,
        @Value("\${app.rsocket.transport.secure.keystore.path}") keyFile: File,
        @Value("\${app.rsocket.transport.secure.keyfile.pass}") pass: String
    ) = PKCS12ClientConnection(trustFile, keyFile, pass)

    @Bean
    @ConditionalOnProperty("app.rsocket.transport.pkcs12")
    fun pkcs12SecureTransport(
        @Value("\${app.rsocket.transport.secure.truststore.path}") trustFile: File,
        @Value("\${app.rsocket.transport.secure.keystore.path}") keyFile: File,
        @Value("\${app.rsocket.transport.secure.keyfile.pass}") pass: String
    ) = JKSSecureConnection(trustFile, keyFile, pass)

}