package com.demo.chat.client.rsocket.config

import com.demo.chat.client.rsocket.core.*
import com.demo.chat.config.CoreClientBeans
import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.TopicMembership
import com.demo.chat.domain.User
import com.demo.chat.service.IKeyService
import com.demo.chat.service.IndexService
import com.demo.chat.service.PersistenceStore
import com.demo.chat.service.TopicPubSubService
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference

class CoreRSocketClients<T, V, Q>(
    private val requesterFactory: RequesterFactory,
    private val coreRSocketProps: CoreRSocketProperties,
    private val keyType: ParameterizedTypeReference<T>
) : CoreClientBeans<T, V, Q> {
    private val logger = LoggerFactory.getLogger(this::class.simpleName)

    override fun keyService(): IKeyService<T> = KeyClient("${coreRSocketProps.key.prefix}", requesterFactory.requester("key"))

    override fun topicExchange(): TopicPubSubService<T, V> =
        TopicPubSubClient("${coreRSocketProps.pubsub.prefix}.", requesterFactory.requester("pubsub"), keyType)

    override fun user(): PersistenceStore<T, User<T>> =
        UserPersistenceClient("${coreRSocketProps.persistence.prefix}user.", requesterFactory.requester("persistence"))

    override fun message(): PersistenceStore<T, Message<T, V>> =
        MessagePersistenceClient("${coreRSocketProps.persistence.prefix}message.", requesterFactory.requester("persistence"))

    override fun topic(): PersistenceStore<T, MessageTopic<T>> =
        TopicPersistenceClient("${coreRSocketProps.persistence.prefix}topic.", requesterFactory.requester("persistence"))

    override fun membership(): PersistenceStore<T, TopicMembership<T>> = MembershipPersistenceClient(
        "${coreRSocketProps.persistence.prefix}membership.",
        requesterFactory.requester("persistence")
    )

    override fun userIndex(): IndexService<T, User<T>, Q> =
        UserIndexClient("${coreRSocketProps.index.prefix}user.", requesterFactory.requester("index"))

    override fun topicIndex(): IndexService<T, MessageTopic<T>, Q> =
        TopicIndexClient("${coreRSocketProps.index.prefix}topic.", requesterFactory.requester("index"))

    override fun membershipIndex(): IndexService<T, TopicMembership<T>, Q> =
        MembershipIndexClient("${coreRSocketProps.index.prefix}membership.", requesterFactory.requester("index"))

    override fun messageIndex(): IndexService<T, Message<T, V>, Q> =
        MessageIndexClient("${coreRSocketProps.index.prefix}message.", requesterFactory.requester("index"))
}