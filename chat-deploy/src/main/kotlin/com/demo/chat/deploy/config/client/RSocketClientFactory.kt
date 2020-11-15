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
import org.springframework.boot.autoconfigure.rsocket.RSocketRequesterAutoConfiguration
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient
import org.springframework.cloud.consul.discovery.ConsulDiscoveryProperties
import org.springframework.cloud.consul.discovery.reactive.ConsulReactiveDiscoveryClient
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.messaging.rsocket.RSocketRequester
import reactor.core.publisher.Mono
import java.util.*

data class AppDiscoveryException(val servicePrefix: String) : RuntimeException("Cannot discover $servicePrefix Service")

@Configuration
@Import(RSocketRequesterAutoConfiguration::class)
class RSocketClientFactory(val builder: RSocketRequester.Builder,
                           client: ConsulClient,
                           props: ConsulDiscoveryProperties) {
    val discovery: ReactiveDiscoveryClient = ConsulReactiveDiscoveryClient(client, props)
    val logger = LoggerFactory.getLogger(this::class.java)

    fun requester(servicePrefix: String): RSocketRequester = discovery
            .getInstances("${servicePrefix}-service-rsocket")
            .map { instance ->
                Optional
                        .ofNullable(instance.metadata["rsocket.port"])
                        .map {
                            builder
                                    .connectTcp(instance.host, it.toInt())
                                    .log()
                                    .block()!!
                        }
                        .orElseThrow { AppDiscoveryException(servicePrefix) }
            }
            .switchIfEmpty(Mono.error(AppDiscoveryException(servicePrefix)))
            .blockFirst()!!

    // TODO - add prefix_requestor_<type> at build-time

    fun <T> keyClient(): IKeyService<T> = KeyClient("key.", requester("key"))

    fun <T> userClient(): PersistenceStore<T, User<T>> = UserPersistenceClient(requester("persistence"))

    fun <T, V> messageClient(): PersistenceStore<T, Message<T, V>> = MessagePersistenceClient(requester("persistence"))

    fun <T> messageTopicClient(): PersistenceStore<T, MessageTopic<T>> = TopicPersistenceClient(requester("persistence"))

    fun <T> topicMembershipClient(): PersistenceStore<T, TopicMembership<T>> = MembershipPersistenceClient(requester("persistence"))

    fun <T> userIndexClient(): IndexService<T, User<T>, Map<String, String>> = UserIndexClient(requester("index"))

    fun <T> topicIndexClient(): IndexService<T, MessageTopic<T>, Map<String, String>> = TopicIndexClient(requester("index"))

    fun <T> membershipIndexClient(): IndexService<T, TopicMembership<T>, Map<String, T>> = MembershipIndexClient(requester("index"))

    fun <T, V> messageIndexClient(): IndexService<T, Message<T, V>, Map<String, T>> = MessageIndexClient(requester("index"))
}