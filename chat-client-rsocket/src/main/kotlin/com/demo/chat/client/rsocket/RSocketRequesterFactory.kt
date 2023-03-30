package com.demo.chat.client.rsocket

import com.demo.chat.service.client.ClientDiscovery
import com.demo.chat.service.client.ClientFactory
import com.demo.chat.service.client.transport.ClientTransportFactory
import io.rsocket.transport.ClientTransport
import org.springframework.cloud.client.ServiceInstance
import org.springframework.messaging.rsocket.RSocketRequester
import java.util.concurrent.ConcurrentHashMap

class RSocketRequesterFactory(
    private val discovery: ClientDiscovery,
    private val builder: RSocketRequester.Builder,
    private val connection: ClientTransportFactory<ClientTransport>,
    private val metadataProvider: () -> Any = { Any() }
) : RequesterFactory {

    private val perHostRequester: MutableMap<ServiceInstance, RSocketRequester> = ConcurrentHashMap()


    override fun getClientForService(serviceName: String): RSocketRequester =
        discovery
            .getServiceInstance(serviceName)
            .map { instance ->

                if (!perHostRequester.containsKey(instance)) {
                    perHostRequester[instance] = builder
                        .transport(connection.getClientTransport(instance.host, instance.port))
                }

                MetadataRSocketRequester(perHostRequester[instance]!!, metadataProvider)
            }
            .block()!!

}