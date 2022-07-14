package com.demo.chat.deploy.app

import com.demo.chat.client.rsocket.config.RSocketClientProperties
import com.demo.chat.deploy.client.consul.config.BaseConsulClientAppConfiguration
import com.demo.chat.secure.rsocket.InsecureConnection
import com.demo.chat.secure.rsocket.PKISecureConnection
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.File

@EnableConfigurationProperties(RSocketClientProperties::class)
class BaseConsulClientApp {
    @Configuration
    class ConsulClientAppConfiguration() : BaseConsulClientAppConfiguration()

    @Bean
    @ConditionalOnProperty("app.rsocket.transport.insecure")
    fun transportFactory() = InsecureConnection()

    @Bean
    @ConditionalOnProperty("app.rsocket.transport.secure")
    fun secureTransport(
        @Value("\${app.rsocket.transport.secure.truststore.path}") trustFile: File,
        @Value("\${app.rsocket.transport.secure.keystore.path}") keyFile: File,
        @Value("\${app.rsocket.transport.secure.keyfile.pass}") pass: String
    ) = PKISecureConnection(trustFile, keyFile, pass)
}