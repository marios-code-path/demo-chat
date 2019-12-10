package com.demo.chat.test.unit

import com.demo.chat.domain.cassandra.ChatEventTopic
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.context.junit.jupiter.SpringExtension

@ExtendWith(SpringExtension::class)
class TopicTests {

    fun roomAssertions(room: ChatEventTopic) {
        assertAll("room contents in tact",
                { Assertions.assertNotNull(room) },
                { Assertions.assertNotNull(room.key.id) },
                { Assertions.assertNotNull(room.name) }
        )
    }
}