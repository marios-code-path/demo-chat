package com.demo.chat.domain

import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.core.env.Environment

/**
 * Supplies the guard beans.
 *
 * Every claim store configuration imports this class. An import is
 * idempotent, so a classpath that names two shared backends still gets one
 * guard. This mirrors how `KeyGenConfiguration` imports
 * [NodeIdConfiguration].
 *
 * This class lives in `com.demo.chat.domain` on purpose.
 * `com.demo.chat.config` is component-scanned by every deployment, so a copy
 * placed there would supply the guard in processes that claim nothing.
 */
@Configuration
@EnableConfigurationProperties(NodeIdClaimProperties::class)
@Import(NodeIdConfiguration::class)
open class NodeIdClaimGuardConfiguration {

    @Bean
    open fun runtimeOwnerId(environment: Environment): RuntimeOwnerId =
        RuntimeOwnerId.generate(environment.getProperty("spring.application.name") ?: "chat")

    @Bean
    open fun nodeIdClaimScheduler(): ClaimScheduler = ExecutorClaimScheduler()

    // ObjectProvider, not List. A List parameter with no candidate bean fails
    // to resolve, and this configuration must also be safe to import early.
    @Bean
    open fun nodeIdClaimGuard(
        stores: ObjectProvider<NodeIdClaimStore>,
        nodeId: NodeId,
        owner: RuntimeOwnerId,
        properties: NodeIdClaimProperties,
        context: ConfigurableApplicationContext,
        scheduler: ClaimScheduler
    ): NodeIdClaimGuard =
        NodeIdClaimGuard(
            stores.orderedStream().toList(), nodeId, owner, properties, context, scheduler
        )
}
