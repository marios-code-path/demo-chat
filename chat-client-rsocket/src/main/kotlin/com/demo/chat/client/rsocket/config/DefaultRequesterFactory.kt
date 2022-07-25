package com.demo.chat.client.rsocket.config

import com.demo.chat.secure.rsocket.TransportFactory
import org.springframework.messaging.rsocket.RSocketRequester

open class DefaultRequesterFactory(
    private val builder: RSocketRequester.Builder,
    private val connection: TransportFactory,
    private val properties: Map<String, RSocketProperty>
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

    override fun serviceDestination(serviceKey: String): String = properties[serviceKey]?.dest!!
}