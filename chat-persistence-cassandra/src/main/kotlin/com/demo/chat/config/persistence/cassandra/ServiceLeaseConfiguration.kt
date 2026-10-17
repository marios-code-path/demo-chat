package com.demo.chat.config.persistence.cassandra

import com.demo.chat.domain.*
import com.demo.chat.persistence.cassandra.impl.CassandraServiceLeaseStore
import org.springframework.beans.factory.ListableBeanFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.cassandra.autoconfigure.CassandraProperties
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate

/** Registers the Cassandra store service lease, its guard and the store write gate. See `CHAT-lswjobhz`. */
@Configuration("cassandraServiceLeaseConfiguration")
@ConditionalOnProperty(name = ["app.service.core.persistence"], havingValue = "cassandra")
@Import(NodeIdClaimGuardConfiguration::class, StoreWriteGateConfiguration::class)
class ServiceLeaseConfiguration {

    @Bean("cassandraServiceLeaseStore")
    fun cassandraServiceLeaseStore(template: ReactiveCassandraTemplate, properties: CassandraProperties): ServiceLeaseStore =
        CassandraServiceLeaseStore(template, NodeIdClaimConfiguration.keyspaceOf(properties))

    @Bean("serviceLeaseGuard")
    fun serviceLeaseGuard(
        @Qualifier("cassandraServiceLeaseStore") store: ServiceLeaseStore,
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
