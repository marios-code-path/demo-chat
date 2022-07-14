package com.demo.chat.deploy.client.consul.config

import com.demo.chat.client.rsocket.config.CoreRSocketServices
import com.demo.chat.client.rsocket.config.DefaultRequesterFactory
import com.demo.chat.client.rsocket.config.RSocketClientProperties
import com.demo.chat.client.rsocket.config.RequesterFactory
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.TypeUtil
import com.demo.chat.secure.rsocket.TransportFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.messaging.rsocket.RSocketStrategies

open class BaseConsulClientAppConfiguration {

    @Bean
    fun requesterBuilder(strategies: RSocketStrategies): RSocketRequester.Builder =
        RSocketRequester.builder().rsocketStrategies(strategies)

    @Bean
    fun requesterFactory(
        builder: RSocketRequester.Builder,
        strategies: RSocketStrategies,
        clientConnectionProps: RSocketClientProperties,
        tcpConnectionFactory: TransportFactory
    ): DefaultRequesterFactory =
        DefaultRequesterFactory(
            builder,
            tcpConnectionFactory,
            clientConnectionProps.config
        )

    @Bean
    fun serviceBeans(
        requesterFactory: RequesterFactory,
        clientProps: RSocketClientProperties
    ) = CoreRSocketServices<Long, String, IndexSearchRequest>(
        requesterFactory,
        clientProps,
        TypeUtil.LongUtil
    )

    @Configuration
    class RSocketServiceBeanConfiguration(services: CoreRSocketServices<Long, String, IndexSearchRequest>) :
        ServiceBeanConfiguration<Long, String, IndexSearchRequest>(services)
}