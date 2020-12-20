package com.demo.chat.deploy.config.client

import com.demo.chat.client.rsocket.core.*
import com.demo.chat.deploy.config.properties.AppConfigurationProperties
import com.demo.chat.deploy.config.properties.RSocketCoreProperties
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.TopicMembership
import com.demo.chat.domain.User
import com.demo.chat.service.IKeyService
import com.demo.chat.service.IndexService
import com.demo.chat.service.PersistenceStore
import com.demo.chat.service.PubSubService
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference

data class AppDiscoveryException(val servicePrefix: String) : RuntimeException("Cannot discover $servicePrefix Service")

class CoreClients(
        private val requesterFactory: RequesterFactory,
        val configProps: AppConfigurationProperties,
) {

    private val coreProps: RSocketCoreProperties = configProps.core
    private val logger = LoggerFactory.getLogger(this::class.simpleName)

    fun <T> keyClient(): IKeyService<T> = KeyClient("${coreProps.key.prefix}", requesterFactory.requester("key"))

    fun <T, V> pubsubClient(t: ParameterizedTypeReference<T>): PubSubService<T, V> = PubSubClient<T, V>("${coreProps.pubsub.prefix}.", requesterFactory.requester("pubsub"), t)

    fun <T> userPersistenceClient(): PersistenceStore<T, User<T>> = UserPersistenceClient("${coreProps.persistence.prefix}user.", requesterFactory.requester("persistence"))

    fun <T, V> messagePersistenceClient(): PersistenceStore<T, Message<T, V>> = MessagePersistenceClient("${coreProps.persistence.prefix}message.", requesterFactory.requester("persistence"))

    fun <T> topicPersistenceClient(): PersistenceStore<T, MessageTopic<T>> = TopicPersistenceClient("${coreProps.persistence.prefix}topic.", requesterFactory.requester("persistence"))

    fun <T> membershipPersistenceClient(): PersistenceStore<T, TopicMembership<T>> = MembershipPersistenceClient("${coreProps.persistence.prefix}membership.", requesterFactory.requester("persistence"))

    fun <T, Q> userIndexClient(): IndexService<T, User<T>, Q> = UserIndexClient("${coreProps.index.prefix}user.", requesterFactory.requester("index"))

    fun <T, Q> topicIndexClient(): IndexService<T, MessageTopic<T>, Q> = TopicIndexClient("${coreProps.index.prefix}topic.", requesterFactory.requester("index"))

    fun <T, Q> membershipIndexClient(): IndexService<T, TopicMembership<T>, Q> = MembershipIndexClient("${coreProps.index.prefix}membership.", requesterFactory.requester("index"))

    fun <T, V, Q> messageIndexClient(): IndexService<T, Message<T, V>, Q> = MessageIndexClient("${coreProps.index.prefix}message.", requesterFactory.requester("index"))
}