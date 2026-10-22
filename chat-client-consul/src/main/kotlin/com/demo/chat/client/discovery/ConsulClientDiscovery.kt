package com.demo.chat.client.discovery

import com.demo.chat.service.client.ClientDiscovery
import com.demo.chat.service.client.ClientProperties
import com.demo.chat.service.client.ClientProperty
import com.demo.chat.service.client.DiscoveryException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.cloud.client.DefaultServiceInstance
import org.springframework.cloud.client.ServiceInstance
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import java.time.Duration

/**
 * Finds one service instance in a Consul registry.
 *
 * A set [coreId] keeps the one instance whose nodeId metadata holds that
 * value. The shell names it with --core-id, so one registry can hold the
 * cores of more than one node. See CHAT-yexqiyie.
 *
 * [registry] names the registry in an error message. It changes no lookup.
 */
class ConsulClientDiscovery(
    private val discovery: ReactiveDiscoveryClient,
    private val configProps: ClientProperties<ClientProperty>,
    private val coreId: Int? = null,
    private val registry: String = "",
    private val retryCount: Long = 10,
    private val retryDelay: Duration = Duration.ofSeconds(5),
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

        // One message for both misses. A core id that matches nothing and an
        // empty registry are the same failure to the caller.
        val missed = buildString {
            append(serviceName).append(" via ").append(serviceDest)
            if (coreId != null) append(", node id ").append(coreId)
            if (registry.isNotBlank()) append(", registry ").append(registry)
        }

        return discovery
            .getInstances(serviceDest)
            .filter { instance -> coreId == null || instance.metadata?.get(NODE_ID) == coreId.toString() }
            .next()
            .map(::advertised)
            .switchIfEmpty(Mono.error(DiscoveryException(missed)))
            .retryWhen(Retry.fixedDelay(retryCount, retryDelay)
                .filter { throwable -> throwable is DiscoveryException }
                .onRetryExhaustedThrow { _, retrySignal ->
                    throw DiscoveryException("$missed, after ${retrySignal.totalRetries()} tries")
                }
            )
    }

    /**
     * Returns the instance at its advertised address.
     *
     * A core in a container registers the address of its container network.
     * A client on the host cannot reach that address. Such a core registers
     * the published host and port as metadata. Without that metadata, or
     * with a port that is not a number, the registered address stays.
     */
    private fun advertised(instance: ServiceInstance): ServiceInstance {
        val metadata = instance.metadata ?: return instance
        val host = metadata[ADVERTISE_HOST]
        if (host.isNullOrBlank()) return instance
        val port = metadata[ADVERTISE_PORT]?.toIntOrNull()
        if (port == null) {
            logger.debug("Instance ${instance.instanceId} advertises host $host and no usable port. "
                + "Its registered address stays.")
            return instance
        }
        return DefaultServiceInstance(
            instance.instanceId, instance.serviceId, host, port, instance.isSecure, metadata,
        )
    }

    companion object {
        private const val NODE_ID = "nodeId"
        private const val ADVERTISE_HOST = "advertiseHost"
        private const val ADVERTISE_PORT = "advertisePort"
    }
}
