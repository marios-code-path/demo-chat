package com.demo.chat.test.rsocket

import com.demo.chat.service.client.ClientFactory
import org.springframework.context.annotation.Bean
import org.springframework.messaging.rsocket.RSocketRequester

class RequesterFactoryTestConfiguration {

    @Bean
    fun requesterFactory(requester: RSocketRequester): ClientFactory<RSocketRequester> {
        return TestRequesterFactory(requester)
    }

}