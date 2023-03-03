package com.demo.chat.service.composite.access

import com.demo.chat.config.controller.composite.CompositeServiceBeansDefinition
import com.demo.chat.service.composite.CompositeServiceBeans
import com.demo.chat.domain.Key
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.secure.access.AccessBroker
import org.reactivestreams.Publisher

class CompositeServiceAccessBeansConfiguration<T, V, Q>(
    private val accessBroker: AccessBroker<T>,
    private val principalKeyPublisher: () -> Publisher<Key<T>>,
    private val rootKeys: RootKeys<T>,
    private val compositeServiceBeansDefinition: CompositeServiceBeansDefinition<T, V, Q>
) : CompositeServiceBeans<T, V> {

    override fun messageService() = MessagingServiceAccess(
        authMetadataAccessBroker = accessBroker,
        principalPublisher = principalKeyPublisher,
        that = compositeServiceBeansDefinition.messageService()
    )

    override fun userService() = UserServiceAccess(
        authMetadataAccessBroker = accessBroker,
        principalSupplier = principalKeyPublisher,
        rootKeys = rootKeys,
        that = compositeServiceBeansDefinition.userService()
    )

    override fun topicService() = TopicServiceControllerAccess(
        authMetadataAccessBroker = accessBroker,
        principalSupplier = principalKeyPublisher,
        rootKeys = rootKeys,
        that = compositeServiceBeansDefinition.topicService()
    )
}