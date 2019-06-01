package com.demo.chat.chatconsumers

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.time.Duration
import java.util.*

@ExtendWith(SpringExtension::class)
@SpringBootTest(classes = [ChatConsumersApplication::class])
class ChatConsumersApplicationTests {

    val logger = LoggerFactory.getLogger(this::class.simpleName)

    @Autowired
    private lateinit var userClient: ChatUserClient

    @Autowired
    private lateinit var roomClient: ChatRoomClient


    private var userName: String = "meatman"
    private var userId: UUID = UUID.fromString("115a7700-8093-11e9-beee-416bd38e6cd4")

}
