package com.demo.chat.client.rsocket.config

import com.demo.chat.client.rsocket.core.*
import com.demo.chat.client.rsocket.core.impl.*
import com.demo.chat.config.CoreClientBeans
import com.demo.chat.domain.*
import com.demo.chat.service.IKeyService
import com.demo.chat.service.IndexService
import com.demo.chat.service.PersistenceStore
import com.demo.chat.service.TopicPubSubService
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.messaging.rsocket.RSocketRequester

class CoreRSocketClients<T, V, Q>(
    private val requesterFactory: RequesterFactory,
    private val clientProperties: ClientRSocketProperties,
    private val keyType: ParameterizedTypeReference<T>
) : CoreClientBeans<T, V, Q> {
    private val logger = LoggerFactory.getLogger(this::class.simpleName)

    private fun serviceKey(key: String) = clientProperties.getServiceConfig(key)

    private val persistenceProps = serviceKey("persistence")
    private val indexProps = serviceKey("index")
    private fun persistenceRequester() =  requesterFactory.requester("persistence")
    private fun indexRequester() =  requesterFactory.requester("index")

    override fun keyService(): IKeyService<T> =
        KeyClient("${serviceKey("key").prefix}", requesterFactory.requester("key"))

    override fun topicExchange(): TopicPubSubService<T, V> =
        TopicPubSubClient("${serviceKey("pubsub").prefix}.", requesterFactory.requester("pubsub"), keyType)

    override fun user(): PersistenceStore<T, User<T>> =
        UserPersistenceClient("${persistenceProps.prefix}user.", persistenceRequester())

    override fun message(): PersistenceStore<T, Message<T, V>> =
        MessagePersistenceClient("${persistenceProps.prefix}message.", persistenceRequester())

    override fun topic(): PersistenceStore<T, MessageTopic<T>> =
        TopicPersistenceClient("${persistenceProps.prefix}topic.", persistenceRequester())

    override fun membership(): PersistenceStore<T, TopicMembership<T>> = MembershipPersistenceClient(
        "${persistenceProps.prefix}membership.",
        persistenceRequester()
    )

    override fun authMetadata(): PersistenceStore<T, AuthMetadata<T>> = AuthMetadataPersistenceClient(
        "${persistenceProps.prefix}authmeta.",
        persistenceRequester()
    )

    override fun userIndex(): IndexService<T, User<T>, Q> =
        UserIndexClient("${indexProps.prefix}user.", indexRequester())

    override fun topicIndex(): IndexService<T, MessageTopic<T>, Q> =
        TopicIndexClient("${indexProps.prefix}topic.", indexRequester())

    override fun membershipIndex(): IndexService<T, TopicMembership<T>, Q> =
        MembershipIndexClient("${indexProps.prefix}membership.", indexRequester())

    override fun messageIndex(): IndexService<T, Message<T, V>, Q> =
        MessageIndexClient("${indexProps.prefix}message.", indexRequester())

    override fun authMetadataIndex(): IndexService<T, AuthMetadata<T>, Q> =
        AuthMetaIndexClient("${indexProps.prefix}authmeta.", indexRequester())
}