package com.demo.chat.test.rsocket

import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.test.context.junit.jupiter.SpringExtension

@ExtendWith(SpringExtension::class)
open class RSocketTestBase {

    @Autowired
    lateinit var requester: RSocketRequester

}