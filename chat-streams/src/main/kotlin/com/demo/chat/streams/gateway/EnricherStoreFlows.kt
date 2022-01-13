package com.demo.chat.streams.gateway

import com.demo.chat.domain.Key
import com.demo.chat.service.EnricherPersistenceStore
import com.demo.chat.service.conflate.KeyEnricherPersistenceStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.integration.annotation.ServiceActivator
import org.springframework.integration.dsl.IntegrationFlow
import reactor.core.publisher.Mono
import java.util.function.Function

interface IntKeyEnricherPersistenceStore<T, V, E> : EnricherPersistenceStore<T, V, E> {
    fun channelPrefix(): String

    @ServiceActivator(async = "true", requiresReply = "true",
    inputChannel = "\${channelPrefix}.add.req",
    outputChannel = "\${channelPrefix}.add.rep")
    override fun add(ent: E): Mono<Void>
}

open class EnricherStoreFlows<T, V, E>
    (
    private val enricherStore: KeyEnricherPersistenceStore<T, V, E>,
    private val keyFn: Function<E, Key<T>>,
    private val channelPrefix: String = "default"
) {

    @Bean
    fun add(
        @Value("\${channelPrefix}.add.req") reqChan: String,
        @Value("\${channelPrefix}.add.res") repChan: String
    ) = IntegrationFlow { flow ->
        flow
            .channel(reqChan)
            .transform<V, Mono<E>> { v ->
                enricherStore.addEnriched(v)
            }
            .channel(repChan)
    }

    @Bean
    fun update(
        @Value("\${channelPrefix}.update.req") reqChan: String,
        @Value("\${channelPrefix}.update.res") repChan: String
    ) = IntegrationFlow { flow ->
        flow
            .channel(reqChan)
            .handle<E> { payload, _ ->
                enricherStore
                    .store.add(payload)
            }
            .channel(repChan)
    }

    @Bean
    fun remove(
        @Value("\${channelPrefix}.rem.req") reqChan: String,
        @Value("\${channelPrefix}.rem.res") repChan: String
    ) = IntegrationFlow { flow ->
        flow
            .channel(reqChan)
            .handle<E> { payload, _ ->
                enricherStore
                    .store.rem(keyFn.apply(payload))
            }
            .channel(repChan)
    }
}