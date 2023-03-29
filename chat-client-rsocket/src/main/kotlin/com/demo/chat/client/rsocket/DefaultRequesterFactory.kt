package com.demo.chat.client.rsocket

import com.demo.chat.config.client.rsocket.RSocketClientProperties
import com.demo.chat.service.client.ClientFactory
import com.demo.chat.service.client.transport.ClientTransportFactory
import io.rsocket.transport.ClientTransport
import org.springframework.messaging.rsocket.RSocketRequester
import java.util.concurrent.ConcurrentHashMap


class DefaultRequesterFactory(
    private val builder: RSocketRequester.Builder,
    private val connection: ClientTransportFactory<ClientTransport>,
    private val clientProps: RSocketClientProperties,
    private val metadataProvider: () -> Any = { Any() }
) : ClientFactory<RSocketRequester> {

    private val perHostRequester: MutableMap<Pair<String, Int>, RSocketRequester> = ConcurrentHashMap()

    private fun getServicePair(serviceKey: String): Pair<String, Int> =
        serviceDestination(serviceKey)
            .split(":")
            .zipWithNext()
            .map { Pair(it.first, it.second.toInt()) }
            .single()

    override fun getClient(serviceKey: String): RSocketRequester {
        val pair = getServicePair(serviceKey)
        if (!perHostRequester.containsKey(pair)) {
            perHostRequester[pair] = builder
                .transport(connection.tcpClientTransport(pair.first, pair.second))
        }
        return MyRequesterWrapper(perHostRequester[pair]!!, metadataProvider)
    }

    override fun serviceDestination(serviceKey: String): String =
        clientProps.getServiceConfig(serviceKey)?.dest!!
}