package com.demo.chat.client.rsocket

import com.demo.chat.service.client.ClientDiscovery
import com.demo.chat.service.client.transport.ClientTransportFactory
import io.rsocket.transport.ClientTransport
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.cloud.client.ServiceInstance
import org.springframework.messaging.rsocket.RSocketRequester
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Supplier

// TODO: STOP BLOCKING!!!!!! This whole thing needs to be reactive!
class RSocketRequesterFactory(
    private val discovery: ClientDiscovery,
    private val builder: RSocketRequester.Builder,
    private val connection: ClientTransportFactory<ClientTransport>,
    private val metadataProvider: Supplier<*> = Supplier { Any() }
) : RequesterFactory {

    private val logger: Logger = LoggerFactory.getLogger(RSocketRequesterFactory::class.java)
    private val perHostRequester: MutableMap<ServiceInstance, RSocketRequester> = ConcurrentHashMap()

    init {
        logger.debug("RSocket Requester Connection provider is: ${connection::class.java}")
    }

    fun nonBlockingGetClientForService(serviceName: String): Mono<out RSocketRequester> =
        discovery
            .getServiceInstance(serviceName)
            .map { instance ->
                if (!perHostRequester.containsKey(instance)) {
                    logger.debug("service instance for $serviceName at ${instance.host} : ${instance.port} ")
                    perHostRequester[instance] = builder
                        .transport(connection.getClientTransport(instance.host, instance.port))
                }
                MetadataRSocketRequester(perHostRequester[instance]!!, metadataProvider)
            }
            .doOnError { t ->
                t.printStackTrace()
            }

    override fun getClientForService(serviceName: String): RSocketRequester = discovery
        .getServiceInstance(serviceName)
        .map { instance ->
            if (!perHostRequester.containsKey(instance)) {
                logger.debug("service instance for $serviceName at ${instance.host} : ${instance.port} ")
                perHostRequester[instance] = builder
                    .transport(connection.getClientTransport(instance.host, instance.port))
            }
            MetadataRSocketRequester(perHostRequester[instance]!!, metadataProvider)
        }
        .doOnError { t ->
            t.printStackTrace()
        }
        .onErrorComplete()
        .block()!!
}