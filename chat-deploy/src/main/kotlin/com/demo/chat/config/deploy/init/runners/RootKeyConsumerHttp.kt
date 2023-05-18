package com.demo.chat.config.deploy.init.runners

import com.demo.chat.config.deploy.event.DeploymentEventPublisher
import com.demo.chat.deploy.event.RootKeyInitializationReadyEvent
import com.demo.chat.domain.Key
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.actuator.RootKey
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.ParameterizedTypeReference
import org.springframework.core.annotation.Order
import org.springframework.http.codec.json.Jackson2JsonDecoder
import org.springframework.http.codec.json.Jackson2JsonEncoder
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.ExchangeFilterFunctions
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient
import java.net.URI

//@Component
//@ConditionalOnProperty(name = ["app.rootkeys.consume.scheme"], havingValue = "http")
class RootKeyConsumerHttp<T>(
    val publisher: DeploymentEventPublisher,
    @Value("\${app.rootkeys.consume.source}")
    val hostURI: String,
    val typeUtil: TypeUtil<T>,
    val mapper: ObjectMapper,
    val rootKeys: RootKeys<T>
) : ApplicationRunner {

    override fun run(args: ApplicationArguments?) {
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
                .bodyToMono(object : ParameterizedTypeReference<Map<String, RootKey>>() {})
                .block()!!

            result.keys.forEach { key ->
                if (result.containsKey(key)) {
                    val domain = result[key]!!
                    rootKeys.addRootKey(key, Key.funKey(typeUtil.assignFrom(domain.id)))
                }
            }

            publisher.publishEvent(RootKeyInitializationReadyEvent(rootKeys))
        }
}