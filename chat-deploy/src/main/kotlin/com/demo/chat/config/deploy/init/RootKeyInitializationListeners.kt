package com.demo.chat.config.deploy.init

import com.demo.chat.config.deploy.event.DeploymentEventPublisher
import org.slf4j.LoggerFactory
import com.demo.chat.deploy.event.RootKeyInitializationReadyEvent
import com.demo.chat.deploy.event.RootKeyUpdatedEvent
import com.demo.chat.deploy.event.StartupAnnouncementEvent
import com.demo.chat.domain.ChatException
import com.demo.chat.domain.Key
import com.demo.chat.domain.TypeUtil
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.service.core.IKeyGenerator
import com.demo.chat.service.core.InitializingKVStore
import com.demo.chat.service.core.RootKeyLoader
import com.demo.chat.service.core.RootKeyStore
import com.demo.chat.service.init.RootKeyService
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationStartedEvent
import org.springframework.context.ApplicationListener
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import org.springframework.core.env.Environment

/**
 * The start of the root keys. See `CHAT-avduuqwp` and `CHAT-bafkgkko`.
 *
 * Two contracts, kept apart:
 *
 * - **Store backed loading.** A node with no consume scheme reaches the
 *   authoritative store. It loads each root, and it creates a missing root with
 *   a conditional write. This is the only place where a root is created.
 * - **Snapshot consumption.** A node with a consume scheme reads a
 *   `RootKeySnapshot` from Consul or from HTTP. It never creates a root.
 *
 * `app.rootkeys.create` is removed. Conditional creation makes it unnecessary.
 * [RootKeySource] validates the choice, and it names the one role that holds
 * no root keys.
 */
@Configuration
class RootKeyInitializationListeners<T : Any>(
    val publisher: DeploymentEventPublisher,
    val typeUtil: TypeUtil<T>
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    fun listenForRootKeyInitialized(): ApplicationListener<RootKeyUpdatedEvent<T>> =
        ApplicationListener { evt ->
            publisher.publishEvent(StartupAnnouncementEvent(RootKeys.rootKeySummary(evt.rootKeys)))
        }

    /**
     * This bean reads the root key source of this process. An unsupported or
     * contradictory setting fails the context refresh. See [RootKeySource].
     */
    @Bean
    fun rootKeySource(env: Environment): RootKeySource = RootKeySource.of(env)

    /**
     * This listener loads the roots from the store of this node, before the
     * node serves a request. A failure stops the start.
     *
     * A [RootKeySource.STORE] process must hold a `RootKeyStore` and an
     * `IKeyGenerator`. A [RootKeySource.NONE] process loads nothing, and a
     * later read of a root in it fails with "the root keys are not loaded".
     */
    @Bean
    @ConditionalOnExpression("'\${app.rootkeys.consume.scheme:}' == ''")
    fun loadRootKeysFromStore(
        source: RootKeySource,
        rootKeys: RootKeys<T>,
        store: ObjectProvider<RootKeyStore<T>>,
        ids: ObjectProvider<IKeyGenerator<T>>,
    ): ApplicationListener<ApplicationStartedEvent> =
        ApplicationListener { _ ->
            if (source == RootKeySource.NONE) {
                logger.info("${RootKeySource.REQUIRED}=false. This process loads no root keys.")
                return@ApplicationListener
            }
            val rootKeyStore = store.ifAvailable ?: throw ChatException(
                "This process needs root keys but has no RootKeyStore. Set ${RootKeySource.SCHEME} to 'kv' or 'http', " +
                    "or set ${RootKeySource.REQUIRED}=false for a role that reads no root."
            )
            val generator = ids.ifAvailable ?: throw ChatException(
                "This node has a root key store but no IKeyGenerator. It cannot create a missing root."
            )
            val roots = RootKeyLoader(rootKeyStore, generator).load().block()
                ?: throw ChatException("The root key load returned no roots.")
            rootKeys.loadDomains(roots.mapValues { (_, id) -> Key.root(id) })
            publisher.publishEvent(StartupAnnouncementEvent("Root Keys Loaded"))
            publisher.publishEvent(RootKeyInitializationReadyEvent(rootKeys))
        }

    @Bean
    @ConditionalOnProperty("app.rootkeys.publish.scheme", havingValue = "kv")
    fun publishRootKeysOnUpdate(rootKeyService: RootKeyService<T>): ApplicationListener<RootKeyUpdatedEvent<T>> =
        ApplicationListener { evt ->
            rootKeyService.publishRootKeys(evt.rootKeys)
            publisher.publishEvent(StartupAnnouncementEvent("Root Keys Update Published"))
        }

    @Bean
    @ConditionalOnProperty("app.kv.rootkeys")
    fun rootKeysService(
        kvStore: InitializingKVStore,
        @Value("\${app.kv.rootkeys}") name: String,
        @Value("\${app.key.type}") keyType: String,
    ) = RootKeyService(kvStore, typeUtil, name, keyType)

    /** `@DependsOn` lets the source check report a missing setting before this bean reads it. */
    @Bean
    @DependsOn("rootKeySource")
    @ConditionalOnProperty("app.rootkeys.consume.scheme", havingValue = "kv")
    fun mergeRootKeysOnStart(
        rootKeys: RootKeys<T>,
        rootKeyService: RootKeyService<T>
    ): ApplicationListener<ApplicationStartedEvent> =
        ApplicationListener { _ ->
            rootKeyService.consumeRootKeys(rootKeys)
            publisher.publishEvent(RootKeyUpdatedEvent(rootKeys))
            publisher.publishEvent(RootKeyInitializationReadyEvent(rootKeys))
        }
}
