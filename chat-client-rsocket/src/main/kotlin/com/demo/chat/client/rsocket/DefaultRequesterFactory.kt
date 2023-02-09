package com.demo.chat.client.rsocket

import com.demo.chat.config.client.rsocket.RSocketClientProperties
import com.demo.chat.secure.transport.TransportFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.rsocket.RSocketRequester

@Configuration
@ConditionalOnProperty("app.client.rsocket.discovery.default")
class DefaultRequesterFactory(
    private val builder: RSocketRequester.Builder,
    private val connection: TransportFactory,
    private val clientProps: RSocketClientProperties
) : RequesterFactory {
    private val perHostRequester: MutableMap<Pair<String, Int>, RSocketRequester> = LinkedHashMap()

    private fun getServicePair(serviceKey: String): Pair<String, Int> =
        serviceDestination(serviceKey)
            .split(":")
            .zipWithNext()
            .map { Pair(it.first, it.second.toInt()) }
            .single()

    override fun requester(serviceKey: String): RSocketRequester {
        val pair = getServicePair(serviceKey)
        if(!perHostRequester.containsKey(pair)) {
            perHostRequester[pair] = builder
                .connect(connection.tcpClientTransport(pair.first, pair.second))
                .log()
                .block()!!
        }
        return perHostRequester[pair]!!
    }

    override fun serviceDestination(serviceKey: String): String = clientProps.getServiceConfig(serviceKey)?.dest!!//properties[serviceKey]?.dest!!
}