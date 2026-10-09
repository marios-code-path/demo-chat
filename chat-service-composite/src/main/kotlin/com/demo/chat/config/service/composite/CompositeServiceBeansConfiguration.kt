package com.demo.chat.config.service.composite

import com.demo.chat.domain.knownkey.RootKeys

import com.demo.chat.service.core.KeyVerifier

import com.demo.chat.service.core.IKeyService

import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.config.IndexServiceBeans
import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.config.PubSubServiceBeans
import com.demo.chat.domain.*
import com.demo.chat.domain.serializers.EmptyMessageUtil
import com.demo.chat.service.composite.impl.MessagingServiceImpl
import com.demo.chat.service.composite.impl.TopicServiceImpl
import com.demo.chat.service.composite.impl.UserServiceImpl
import com.demo.chat.service.security.RoomMemberGrant
import com.demo.chat.service.security.RoomOwnerGrant
import com.demo.chat.config.CommandBusSettings
import com.demo.chat.service.command.SubmitterIdentity
import com.demo.chat.service.composite.command.memory.MemoryCommandRuntime
import com.demo.chat.service.composite.command.publication.RoomPublications
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty("app.service.composite")
class CompositeServiceBeansConfiguration<T : Any, V, Q>(
    val persistenceBeans: PersistenceServiceBeans<T, V>,
    val indexBeans: IndexServiceBeans<T, V, Q>,
    val pubsub: PubSubServiceBeans<T, V>,
    val typeUtil: TypeUtil<T>,
    private val emptyMessageSupplier: EmptyMessageUtil<V>,
    private val queryConverters: RequestToQueryConverters<Q>,
    private val commandRuntime: MemoryCommandRuntime<T, V>,
    private val publications: RoomPublications<T, V>,
    private val commandSettings: CommandBusSettings,
    private val submitters: ObjectProvider<SubmitterIdentity<T>>,
    private val roomOwnerGrants: ObjectProvider<RoomOwnerGrant<T>>,
    private val roomMemberGrants: ObjectProvider<RoomMemberGrant<T>>,
    keyService: IKeyService<T>,
    private val rootKeys: RootKeys<T>,
) : CompositeServiceBeans<T, V> {

    /** Every inbound id resolves through the registry. See `CHAT-avduuqwp`. */
    private val verifier = KeyVerifier(keyService, rootKeys)

    @Bean
    override fun messageService() = MessagingServiceImpl(
        messageIndex = indexBeans.messageIndex(),
        messagePersistence = persistenceBeans.messagePersistence(),
        publications = publications,
        topicIdToQuery = queryConverters::topicIdToQuery,
        typeUtil = typeUtil,
        verifier = verifier,
        commandBus = commandRuntime.bus,
        completions = commandRuntime.completions,
        submitter = submitters.ifAvailable,
        requirement = commandSettings.requirement,
        timeout = commandSettings.timeout,
        rootKeys = rootKeys,
    )

    @Bean
    override fun topicService() = TopicServiceImpl(
        topicPersistence = persistenceBeans.topicPersistence(),
        topicIndex = indexBeans.topicIndex(),
        pubsub = pubsub.pubSubService(),
        userPersistence = persistenceBeans.userPersistence(),
        membershipPersistence = persistenceBeans.membershipPersistence(),
        membershipIndex = indexBeans.membershipIndex(),
        emptyDataCodec = emptyMessageSupplier,
        topicNameToQuery = queryConverters::topicNameToQuery,
        memberOfIdToQuery = queryConverters::membershipIdToQuery,
        memberWithTopicToQuery = queryConverters::membershipRequestToQuery,
        messagePersistence = persistenceBeans.messagePersistence(),
        verifier = verifier,
        rootKeys = rootKeys,
        roomOwnerGrant = roomOwnerGrants.ifAvailable,
        roomMemberGrant = roomMemberGrants.ifAvailable,
    )

    @Bean
    override fun userService() = UserServiceImpl<T, Q>(
        userPersistence = persistenceBeans.userPersistence(),
        userIndex = indexBeans.userIndex(),
        userHandleToQuery = queryConverters::userHandleToQuery,
        verifier = verifier,
        rootKeys = rootKeys,
    )
}

//
//@Configuration
//@ConditionalOnProperty("app.service.composite.security")
//class SpringSecurityCompositeServiceAccessBeans<T>(
//    accessBroker: AccessBroker<T>,
//    rootKeys: RootKeys<T>,
//    compositeServiceBeansDefinition: CompositeServiceBeansDefinition<T, String, IndexSearchRequest>
//) : CompositeServiceBeans<T, String> by CompositeServiceAccessBeansConfiguration(
//    accessBroker = accessBroker,
//    principalKeyPublisher = {
//        ReactiveSecurityContextHolder.getContext()
//            .map { it.authentication.principal as ChatUserDetails<T> }
//            .map { it.user.key }
//    },
//    rootKeys = rootKeys,
//    compositeServiceBeansDefinition = compositeServiceBeansDefinition
//)
