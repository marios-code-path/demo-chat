package com.demo.chat.client.rsocket.config

import org.springframework.messaging.rsocket.RSocketRequester

class DefaultRequesterFactory(
    private val builder: RSocketRequester.Builder,
    private val connection: SecureConnection,
    private val properties: Map<String, RSocketProperty>
) : RequesterFactory {
    private fun getServicePair(serviceKey: String): Pair<String, Int> =
        serviceDestination(serviceKey).split(":")
            .zipWithNext()
            .map { Pair(it.first, it.second.toInt()) }
            .single()

    override fun requester(serviceKey: String): RSocketRequester {
        val pair = getServicePair(serviceKey)
        return builder
            .connect(connection.tcpClientTransport(pair.first, pair.second, false))
            .log()
            .block()!!
    }

    override fun serviceDestination(serviceKey: String): String = properties[serviceKey]?.dest!!
}