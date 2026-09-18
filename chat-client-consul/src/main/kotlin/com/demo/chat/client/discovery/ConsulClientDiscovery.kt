package com.demo.chat.client.discovery

import com.demo.chat.service.client.ClientDiscovery
import com.demo.chat.service.client.ClientProperties
import com.demo.chat.service.client.ClientProperty
import com.demo.chat.service.client.DiscoveryException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.cloud.client.ServiceInstance
import org.springframework.cloud.consul.discovery.reactive.ConsulReactiveDiscoveryClient
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import java.time.Duration

class ConsulClientDiscovery(
    private val discovery: ConsulReactiveDiscoveryClient,
    private val configProps: ClientProperties<ClientProperty>,
) : ClientDiscovery {

    val logger: Logger = LoggerFactory.getLogger(ClientDiscovery::class.java)

    override fun getServiceInstance(serviceName: String): Mono<ServiceInstance> {
        // ClientProperty.dest is nullable in this repository, and Spring
        // Cloud 5 requires a non-null service id. A null destination is a
        // misconfiguration, and no default can stand in for it, so this
        // names the service rather than discovering nothing.
        val serviceDest = checkNotNull(configProps.getServiceConfig(serviceName).dest) {
            "the client configuration for $serviceName declares no dest"
        }
        logger.debug("Client Discovering $serviceName via $serviceDest")

        return discovery
            .getInstances(serviceDest)
            .next()
            .switchIfEmpty(Mono.error(DiscoveryException("$serviceName via ${configProps.getServiceConfig(serviceName).dest}")))
            .retryWhen(Retry.fixedDelay(10, Duration.ofSeconds(5))
                .filter { throwable -> throwable is DiscoveryException }
                .onRetryExhaustedThrow { _, retrySignal ->
                    throw DiscoveryException("failed after ${retrySignal.totalRetries()}")
                }
            )
    }

}