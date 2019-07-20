package com.demo.chat.chatconsumers

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit.jupiter.SpringExtension

@ExtendWith(SpringExtension::class)
@SpringBootTest(classes = [ChatConsumersApplication::class])
@Disabled
class MessageConsumerTests {

    val logger = LoggerFactory.getLogger(this::class.simpleName)


}