package com.demo.chat.config.persistence.redis

import com.demo.chat.domain.*
import com.demo.chat.persistence.redis.impl.RedisServiceLeaseStore
import org.springframework.beans.factory.ListableBeanFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.ReactiveStringRedisTemplate

/**
 * Registers the Redis store service lease, its guard and the store write
 * gate. See `CHAT-lswjobhz`.
 *
 * A lease lives in the persistence backend, so the condition reads
 * `app.service.core.persistence` alone. The guard takes the store as a
 * constructor parameter, so Spring destroys the guard before the store.
 */
@Configuration("redisServiceLeaseConfiguration")
@ConditionalOnProperty(name = ["app.service.core.persistence"], havingValue = "redis")
@Import(NodeIdClaimGuardConfiguration::class, StoreWriteGateConfiguration::class)
class ServiceLeaseConfiguration {

    @Bean("redisServiceLeaseStore")
    fun redisServiceLeaseStore(
        template: ReactiveStringRedisTemplate,
        @Value("\${app.key.type}") keyType: String,
    ): ServiceLeaseStore = RedisServiceLeaseStore(template, keyType)

    @Bean("serviceLeaseGuard")
    fun serviceLeaseGuard(
        @Qualifier("redisServiceLeaseStore") store: ServiceLeaseStore,
        gate: StoreWriteGate,
        beans: ListableBeanFactory,
        nodeId: NodeId,
        owner: RuntimeOwnerId,
        properties: NodeIdClaimProperties,
        context: ConfigurableApplicationContext,
    ): ServiceLeaseGuard = ServiceLeaseGuard(
        store, { ServiceLeaseNames.servicesIn(beans) }, gate, nodeId, owner, properties, context,
        ExecutorClaimScheduler("service-lease-renew"),
        deadlines = ExecutorClaimScheduler("service-lease-deadline"),
    )
}
