package com.demo.chat.deploy.config.client

import com.demo.chat.client.rsocket.*
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.TopicMembership
import com.demo.chat.domain.User
import com.demo.chat.service.IKeyService
import com.demo.chat.service.IndexService
import com.demo.chat.service.PersistenceStore
import com.ecwid.consul.v1.ConsulClient
import org.slf4j.LoggerFactory
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient
import org.springframework.cloud.consul.discovery.ConsulDiscoveryProperties
import org.springframework.cloud.consul.discovery.reactive.ConsulReactiveDiscoveryClient
import org.springframework.messaging.rsocket.RSocketRequester
import reactor.core.publisher.Mono
import java.util.*

data class AppDiscoveryException(val servicePrefix: String) : RuntimeException("Cannot discover $servicePrefix Service")

interface RSocketClientProperties {
    val key: String
    val index: String
    val persistence: String
    val messaging: String
}

class CoreServiceClientFactory(
        private val builder: RSocketRequester.Builder,
        client: ConsulClient,
        props: ConsulDiscoveryProperties,
        private val clientProps: RSocketClientProperties,
) {
    val discovery: ReactiveDiscoveryClient = ConsulReactiveDiscoveryClient(client, props)
    val logger = LoggerFactory.getLogger(this::class.simpleName)

    private fun getServiceId(serviceType: String) = when (serviceType) {
        "key" -> clientProps.key
        "index" -> clientProps.index
        "persistence" -> clientProps.persistence
        "messaging" -> clientProps.messaging
        else -> throw AppDiscoveryException(serviceType)
    }.apply { println("$serviceType = $this") }

    fun requester(serviceType: String): RSocketRequester {
        return discovery
                .getInstances(getServiceId(serviceType))
                .map { instance ->
                    Optional
                            .ofNullable(instance.metadata["rsocket.port"])
                            .map {
                                builder
                                        .connectTcp(instance.host, it.toInt())
                                        .log()
                                        .block()!!
                            }
                            .orElseThrow { AppDiscoveryException(serviceType) }
                }
                .switchIfEmpty(Mono.error(AppDiscoveryException(serviceType)))
                .blockFirst()!!
    }

    fun <T> keyClient(): IKeyService<T> = KeyClient("key.", requester("key"))

    fun <T> userClient(): PersistenceStore<T, User<T>> = UserPersistenceClient(requester("persistence"))

    fun <T, V> messageClient(): PersistenceStore<T, Message<T, V>> = MessagePersistenceClient(requester("persistence"))

    fun <T> messageTopicClient(): PersistenceStore<T, MessageTopic<T>> = TopicPersistenceClient(requester("persistence"))

    fun <T> topicMembershipClient(): PersistenceStore<T, TopicMembership<T>> = MembershipPersistenceClient(requester("persistence"))

    fun <T, Q> userIndexClient(): IndexService<T, User<T>, Q> = UserIndexClient(requester("index"))

    fun <T, Q> topicIndexClient(): IndexService<T, MessageTopic<T>, Q> = TopicIndexClient(requester("index"))

    fun <T, Q> membershipIndexClient(): IndexService<T, TopicMembership<T>, Q> = MembershipIndexClient(requester("index"))

    fun <T, V, Q> messageIndexClient(): IndexService<T, Message<T, V>, Q> = MessageIndexClient(requester("index"))
}