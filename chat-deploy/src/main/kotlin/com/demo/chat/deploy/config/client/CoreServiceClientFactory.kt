package com.demo.chat.deploy.config.client

import com.demo.chat.client.rsocket.core.*
import com.demo.chat.deploy.config.properties.AppConfigurationProperties
import com.demo.chat.deploy.config.properties.RSocketCoreProperties
import com.demo.chat.deploy.config.properties.RSocketEdgeProperties
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.TopicMembership
import com.demo.chat.domain.User
import com.demo.chat.service.IKeyService
import com.demo.chat.service.IndexService
import com.demo.chat.service.PersistenceStore
import com.demo.chat.service.PubSubTopicExchangeService
import com.ecwid.consul.v1.ConsulClient
import org.slf4j.LoggerFactory
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient
import org.springframework.cloud.consul.discovery.ConsulDiscoveryProperties
import org.springframework.cloud.consul.discovery.reactive.ConsulReactiveDiscoveryClient
import org.springframework.core.ParameterizedTypeReference
import org.springframework.messaging.rsocket.RSocketRequester
import reactor.core.publisher.Mono
import java.util.*

data class AppDiscoveryException(val servicePrefix: String) : RuntimeException("Cannot discover $servicePrefix Service")

class CoreServiceClientFactory(
        private val builder: RSocketRequester.Builder,
        client: ConsulClient,
        props: ConsulDiscoveryProperties,
        configProps: AppConfigurationProperties
) {
    private val coreProps: RSocketCoreProperties = configProps.core
    private val edgeProps: RSocketEdgeProperties = configProps.edge

    val discovery: ReactiveDiscoveryClient = ConsulReactiveDiscoveryClient(client, props)
    val logger = LoggerFactory.getLogger(this::class.simpleName)

    private fun getServiceId(serviceType: String) = when (serviceType) {
        "key" -> coreProps.key.dest
        "index" -> coreProps.index.dest
        "persistence" -> coreProps.persistence.dest
        "pubsub" -> coreProps.pubsub.dest
        "topic" -> edgeProps.topic.dest
        "user" -> edgeProps.user.dest
        "message" -> edgeProps.message.dest
        else -> throw AppDiscoveryException(serviceType)
    }

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

    fun <T> keyClient(): IKeyService<T> = KeyClient("${coreProps.key.prefix}", requester("key"))

    fun <T, V> pubsubClient(t: ParameterizedTypeReference<T>): PubSubTopicExchangeService<T, V> = PubSubClient<T, V>("${coreProps.pubsub.prefix}", requester("pubsub"), t)

    fun <T> userPersistenceClient(): PersistenceStore<T, User<T>> = UserPersistenceClient("${coreProps.persistence.prefix}user", requester("persistence"))

    fun <T, V> messagePersistenceClient(): PersistenceStore<T, Message<T, V>> = MessagePersistenceClient("${coreProps.persistence.prefix}message", requester("persistence"))

    fun <T> topicPersistenceClient(): PersistenceStore<T, MessageTopic<T>> = TopicPersistenceClient("${coreProps.persistence.prefix}topic", requester("persistence"))

    fun <T> membershipPersistenceClient(): PersistenceStore<T, TopicMembership<T>> = MembershipPersistenceClient("${coreProps.persistence.prefix}membership", requester("persistence"))

    fun <T, Q> userIndexClient(): IndexService<T, User<T>, Q> = UserIndexClient("${coreProps.index.prefix}user", requester("index"))

    fun <T, Q> topicIndexClient(): IndexService<T, MessageTopic<T>, Q> = TopicIndexClient("${coreProps.index.prefix}topic", requester("index"))

    fun <T, Q> membershipIndexClient(): IndexService<T, TopicMembership<T>, Q> = MembershipIndexClient("${coreProps.index.prefix}membership", requester("index"))

    fun <T, V, Q> messageIndexClient(): IndexService<T, Message<T, V>, Q> = MessageIndexClient("${coreProps.index.prefix}message", requester("index"))
}