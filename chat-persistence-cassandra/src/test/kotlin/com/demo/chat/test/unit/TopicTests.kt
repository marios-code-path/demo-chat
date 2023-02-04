package com.demo.chat.test.unit

import com.demo.chat.persistence.cassandra.domain.ChatTopic
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.util.*

@ExtendWith(SpringExtension::class)
class TopicTests {

    fun roomAssertions(room: ChatTopic<UUID>) {
        assertAll("room contents in tact",
                { Assertions.assertNotNull(room) },
                { Assertions.assertNotNull(room.key.id) },
                { Assertions.assertNotNull(room.data) }
        )
    }
}