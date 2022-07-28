package com.demo.chat.client.rsocket.config

import com.demo.chat.client.rsocket.core.KeyClient
import com.demo.chat.client.rsocket.core.TopicPubSubClient
import com.demo.chat.client.rsocket.core.impl.*
import com.demo.chat.config.CoreServices
import com.demo.chat.domain.TypeUtil
import com.demo.chat.service.*
import com.demo.chat.service.security.AuthMetaIndex
import com.demo.chat.service.security.AuthMetaPersistence

class CoreRSocketServiceDefinitions<T, V, Q>(
    private val requesterFactory: RequesterFactory,
    private val clientProperties: RSocketClientProperties,
    private val typeUtil: TypeUtil<T>,
) : CoreServices<T, V, Q> {
    private fun serviceKey(key: String) = clientProperties.getServiceConfig(key)

    private val persistenceProps = serviceKey("persistence")
    private val indexProps = serviceKey("index")
    private fun persistenceRequester() = requesterFactory.requester("persistence")
    private fun indexRequester() = requesterFactory.requester("index")

    override fun keyService(): IKeyService<T> =
        KeyClient("${serviceKey("key").prefix}", requesterFactory.requester("key"))

    override fun topicExchange(): TopicPubSubService<T, V> =
        TopicPubSubClient("${serviceKey("pubsub").prefix}.", requesterFactory.requester("pubsub"), typeUtil)

    override fun userPersistence(): UserPersistence<T> =
        UserPersistenceClient("${persistenceProps.prefix}user.", persistenceRequester())

    override fun messagePersistence(): MessagePersistence<T, V> =
        MessagePersistenceClient("${persistenceProps.prefix}message.", persistenceRequester())

    override fun topicPersistence(): TopicPersistence<T> =
        TopicPersistenceClient("${persistenceProps.prefix}topic.", persistenceRequester())

    override fun membershipPersistence(): MembershipPersistence<T> = MembershipPersistenceClient(
        "${persistenceProps.prefix}membership.",
        persistenceRequester()
    )

    override fun authMetaPersistence(): AuthMetaPersistence<T> =
        AuthMetadataPersistenceClient(
            "${persistenceProps.prefix}authmetadata.",
            persistenceRequester()
        )

    override fun userIndex(): UserIndexService<T, Q> =
        UserIndexClient("${indexProps.prefix}user.", indexRequester())

    override fun topicIndex(): TopicIndexService<T, Q> =
        TopicIndexClient("${indexProps.prefix}topic.", indexRequester())

    override fun membershipIndex(): MembershipIndexService<T, Q> =
        MembershipIndexClientImpl("${indexProps.prefix}membership.", indexRequester())

    override fun messageIndex(): MessageIndexService<T, V, Q> =
        MessageIndexClient("${indexProps.prefix}message.", indexRequester())

    override fun authMetadataIndex(): AuthMetaIndex<T, Q> =
        AuthMetaIndexClient("${indexProps.prefix}authmetadata.", indexRequester())
}