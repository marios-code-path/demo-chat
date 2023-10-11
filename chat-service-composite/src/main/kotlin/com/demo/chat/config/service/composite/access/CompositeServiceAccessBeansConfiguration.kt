package com.demo.chat.config.service.composite.access

import com.demo.chat.config.service.composite.CompositeServiceBeansConfiguration
import com.demo.chat.domain.Key
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.service.composite.access.MessagingServiceAccess
import com.demo.chat.service.composite.access.TopicServiceAccess
import com.demo.chat.service.composite.access.UserServiceAccess
import com.demo.chat.service.security.AccessBroker
import org.reactivestreams.Publisher

// TODO... refactor into a message pattern instead of ...
class CompositeServiceAccessBeansConfiguration<T, V, Q>(
    private val accessBroker: AccessBroker<T>,
    private val principalKeyPublisher: () -> Publisher<Key<T>>,
    private val rootKeys: RootKeys<T>,
    private val compositeServiceBeansConfiguration: CompositeServiceBeansConfiguration<T, V, Q>
) : CompositeServiceBeans<T, V> {

    override fun messageService() = MessagingServiceAccess(
        authMetadataAccessBroker = accessBroker,
        principalPublisher = principalKeyPublisher,
        that = compositeServiceBeansConfiguration.messageService()
    )

    override fun userService() = UserServiceAccess(
        authMetadataAccessBroker = accessBroker,
        principalSupplier = principalKeyPublisher,
        rootKeys = rootKeys,
        that = compositeServiceBeansConfiguration.userService()
    )

    override fun topicService() = TopicServiceAccess(
        authMetadataAccessBroker = accessBroker,
        principalSupplier = principalKeyPublisher,
        rootKeys = rootKeys,
        that = compositeServiceBeansConfiguration.topicService()
    )
}