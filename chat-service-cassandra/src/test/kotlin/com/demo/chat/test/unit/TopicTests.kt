package com.demo.chat.test.unit

import com.demo.chat.domain.ChatTopic
import com.demo.chat.domain.ChatTopicKey
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.time.Instant
import java.util.*
import kotlin.collections.HashSet

@ExtendWith(SpringExtension::class)
class TopicTests {

    fun roomAssertions(room: ChatTopic) {
        assertAll("room contents in tact",
                { Assertions.assertNotNull(room) },
                { Assertions.assertNotNull(room.key.id) },
                { Assertions.assertNotNull(room.name) }
        )
    }
}