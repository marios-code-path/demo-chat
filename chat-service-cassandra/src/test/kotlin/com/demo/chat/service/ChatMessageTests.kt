package com.demo.chat.service

import com.demo.chat.domain.ChatMessage
import com.demo.chat.domain.ChatMessageKey
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.time.Instant
import java.util.*

@ExtendWith(SpringExtension::class)
class ChatMessageTests {

    @Test
    fun testShouldHoldState() {
        val userUUID = UUID.randomUUID()
        val msgUUID = UUID.randomUUID()
        val roomUUID = UUID.randomUUID()

        val randomBody = UUID.randomUUID().toString()

        val message = ChatMessage(ChatMessageKey(msgUUID, userUUID, roomUUID, Instant.now()), randomBody, true)

        StepVerifier
                .create(Flux.just(message))
                .assertNext { chatMessageAssertion(it, randomBody)}
                .verifyComplete()
    }

}