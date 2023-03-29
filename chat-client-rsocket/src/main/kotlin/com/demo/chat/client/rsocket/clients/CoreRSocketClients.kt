package com.demo.chat.client.rsocket.clients

import com.demo.chat.client.rsocket.clients.core.KeyClient
import com.demo.chat.client.rsocket.clients.core.TopicPubSubClient
import com.demo.chat.client.rsocket.clients.core.config.*
import com.demo.chat.config.CoreServices
import com.demo.chat.config.client.rsocket.RSocketClientProperties
import com.demo.chat.domain.TypeUtil
import com.demo.chat.service.client.ClientFactory
import com.demo.chat.service.client.ClientProperty
import com.demo.chat.service.core.*
import com.demo.chat.service.security.AuthMetaIndex
import com.demo.chat.service.security.AuthMetaPersistence
import com.demo.chat.service.security.SecretsStore
import org.springframework.messaging.rsocket.RSocketRequester

/**
 * This is a bean that declares only the local clients for the core services
 */
class CoreRSocketClients<T, V, Q>(
    private val requesterFactory: ClientFactory<RSocketRequester>,
    private val clientProperties: RSocketClientProperties,
    private val typeUtil: TypeUtil<T>,
) : CoreServices<T, V, Q> {

    private fun serviceKey(key: String): ClientProperty = clientProperties.getServiceConfig(key)

    private val secretsProps = serviceKey("secrets")
    private val persistenceProps = serviceKey("persistence")
    private val indexProps = serviceKey("index")
    private val pubSubProps = serviceKey("pubsub")
    private val keyProps = serviceKey("key")

    private fun persistenceRequester() = requesterFactory.getClient("persistence")
    private fun indexRequester() = requesterFactory.getClient("index")
    private fun keyRequester() = requesterFactory.getClient("key")
    private fun pubSubRequester() = requesterFactory.getClient("pubsub")
    private fun secretsRequester() = requesterFactory.getClient("secrets")

    override fun keyService(): IKeyService<T> =
        KeyClient("${keyProps.prefix}", keyRequester())

    override fun pubSubService(): TopicPubSubService<T, V> =
        TopicPubSubClient("${pubSubProps.prefix}", pubSubRequester(), typeUtil)

    override fun secretsStore(): SecretsStore<T> =
        SecretsClient("${secretsProps.prefix}", secretsRequester())

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