package com.demo.chat.config.deploy.init

import com.demo.chat.config.deploy.event.DeploymentEventPublisher
import com.demo.chat.domain.knownkey.RootKeySnapshot
import com.demo.chat.domain.ChatException
import com.demo.chat.deploy.event.RootKeyInitializationReadyEvent
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.config.JACKSON_2_OBJECT_MAPPER
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationStartedEvent
import org.springframework.context.ApplicationListener
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import org.springframework.http.codec.json.Jackson2JsonDecoder
import org.springframework.http.codec.json.Jackson2JsonEncoder
import org.springframework.web.reactive.function.client.ExchangeFilterFunctions
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient
import java.net.URI

@Configuration
@ConditionalOnProperty(name = ["app.rootkeys.consume.scheme"], havingValue = "http")
class HttpRootKeyConsumeOnStart(val publisher: DeploymentEventPublisher) {

    /** `@DependsOn` lets the source check report a missing source before this bean reads it. */
    @Bean
    @DependsOn("rootKeySource")
    fun <T> captureRootKeys(
        @Value("\${app.rootkeys.consume.source}") hostURI: String,
        typeUtil: TypeUtil<T>,
        @Value("\${app.key.type}") keyType: String,
        @Qualifier(JACKSON_2_OBJECT_MAPPER) mapper: ObjectMapper,
        rootKeys: RootKeys<T>
    ): ApplicationListener<ApplicationStartedEvent> =
        ApplicationListener { _ ->
            val exchangeStrategies = ExchangeStrategies.builder()
                .codecs { configurer ->
                    configurer.defaultCodecs().jackson2JsonEncoder(Jackson2JsonEncoder(mapper))
                    configurer.defaultCodecs().jackson2JsonDecoder(Jackson2JsonDecoder(mapper))
                }
                .build()

            val uri = URI(hostURI)
            val host = uri.host
            val port = uri.port

            val client = WebClient.builder()
                .exchangeStrategies(exchangeStrategies)
                .filter(ExchangeFilterFunctions.basicAuthentication("actuator", "actuator"))
                .baseUrl("http://${host}:${port}")
                .build()

            val result = client.get()
                .uri("/actuator/rootkeys")
                .retrieve()
                .bodyToMono(RootKeySnapshot::class.java)
                .block()
                ?: throw ChatException("The root key source $hostURI returned no snapshot.")
            result.load(rootKeys, keyType, typeUtil)

            publisher.publishEvent(RootKeyInitializationReadyEvent(rootKeys))
        }
}