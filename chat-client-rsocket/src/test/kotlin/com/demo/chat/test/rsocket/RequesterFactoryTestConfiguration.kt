package com.demo.chat.test.rsocket

import com.demo.chat.client.rsocket.RequesterFactory
import org.springframework.context.annotation.Bean
import org.springframework.messaging.rsocket.RSocketRequester

class RequesterFactoryTestConfiguration {

    @Bean
    fun requesterFactory(requester: RSocketRequester): RequesterFactory {
        return TestRequesterFactory(requester)
    }

}