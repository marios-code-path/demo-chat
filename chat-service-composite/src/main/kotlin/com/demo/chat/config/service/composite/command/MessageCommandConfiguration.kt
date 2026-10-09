package com.demo.chat.config.service.composite.command

import com.demo.chat.config.CommandBusSettings
import com.demo.chat.config.IndexServiceBeans
import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.config.PubSubServiceBeans
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.command.DomainCommandHandler
import com.demo.chat.service.composite.command.handler.MessageIndexHandler
import com.demo.chat.service.composite.command.handler.MessagePersistenceHandler
import com.demo.chat.service.composite.command.handler.MessagePubSubHandler
import com.demo.chat.service.composite.command.handler.MessageVectorHandler
import com.demo.chat.service.composite.command.memory.MemoryCommandRuntime
import com.demo.chat.service.composite.command.publication.RoomPublications
import com.demo.chat.service.core.IKeyGenerator
import com.demo.chat.service.core.IKeyService
import com.demo.chat.service.core.KeyAllocator
import com.demo.chat.service.vector.MessageVectorIndexer
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment

/**
 * The Stage 1 command beans. `CommandBusValidation` admits only
 * `app.command.bus=memory`, so this configuration builds the memory runtime.
 * An inactive vector provider adds no `V` handler and no `V` obligation.
 */
@Configuration
@ConditionalOnProperty("app.service.composite")
class MessageCommandConfiguration<T : Any, V, Q>(
    private val persistenceBeans: PersistenceServiceBeans<T, V>,
    private val indexBeans: IndexServiceBeans<T, V, Q>,
    private val pubsub: PubSubServiceBeans<T, V>,
    private val keyService: IKeyService<T>,
    private val keyGenerator: IKeyGenerator<T>,
    private val rootKeys: RootKeys<T>,
    private val typeUtil: TypeUtil<T>,
    private val vectorIndexers: ObjectProvider<MessageVectorIndexer<T>>,
    private val environment: Environment,
) {
    @Bean
    fun roomPublications(): RoomPublications<T, V> = RoomPublications(pubsub.pubSubService())

    @Bean
    fun commandBusSettings(): CommandBusSettings = CommandBusSettings.from(environment)

    @Bean(destroyMethod = "close")
    fun memoryCommandRuntime(publications: RoomPublications<T, V>, settings: CommandBusSettings): MemoryCommandRuntime<T, V> =
        MemoryCommandRuntime(KeyAllocator(keyGenerator, rootKeys), typeUtil, handlers(publications), settings)

    private fun handlers(publications: RoomPublications<T, V>): List<DomainCommandHandler<T, V>> = listOfNotNull(
        MessagePersistenceHandler(keyService, persistenceBeans.messagePersistence()),
        MessageIndexHandler(indexBeans.messageIndex()),
        vectorIndexers.ifAvailable?.let { MessageVectorHandler<T, V>(it) },
        MessagePubSubHandler(publications),
    )
}
