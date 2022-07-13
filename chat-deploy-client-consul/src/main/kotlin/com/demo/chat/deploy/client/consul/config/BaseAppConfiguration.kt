package com.demo.chat.deploy.client.consul.config

import com.demo.chat.client.rsocket.config.CoreRSocketClients
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

class BaseAppConfiguration {

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
    fun clientBeans(
        requesterFactory: RequesterFactory,
        clientProps: RSocketClientProperties
    ) = CoreRSocketClients<Long, String, IndexSearchRequest>(
        requesterFactory,
        clientProps,
        TypeUtil.LongUtil
    )

    @Configuration
    class RSocketRSocketClientBeansConfiguration(clients: CoreRSocketClients<Long, String, IndexSearchRequest>) :
        RSocketClientBeansConfiguration<Long, String, IndexSearchRequest>(clients)
}