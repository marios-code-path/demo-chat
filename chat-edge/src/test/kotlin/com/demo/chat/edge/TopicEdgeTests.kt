package com.demo.chat.edge

import com.demo.chat.domain.InfoAlert
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.test.StepVerifier
import java.time.Duration

@ExtendWith(SpringExtension::class)
class TopicEdgeTests {

    lateinit var topicEdge: ChatTopicEdge

    @Test
    fun `verify should subscribe to topic and get first info alert`() {
        val sub = topicEdge
                .subscribeToTopic(testUserId(), testRoomId())
                .thenMany(topicEdge.receiveTopicEvents(testUserId()))

        StepVerifier
                .create(sub)
                .assertNext {
                    when (it) {
                        is InfoAlert -> infoAlertAssertion(it)
                        else -> {
                            AssertionError("The first message was not alert")
                        }
                    }
                }
    }

    @Test
    fun `verify should unsubscribe from topic and receive closing events`() {
        val sub = topicEdge
                .subscribeToTopic(testUserId(), testRoomId())
                .thenMany(topicEdge.receiveTopicEvents(testUserId()))

        StepVerifier
                .create(sub)
                .assertNext {
                    infoAlertAssertion(it)
                }
                .thenAwait(Duration.ofMillis(1000))
                .then {
                    topicEdge.closeTopic(testRoomId()).subscribe()
                }
                .thenAwait(Duration.ofMillis(1000))
                .assertNext {
                    infoAlertAssertion(it)  // should be Leave Alert
                }
                .assertNext {
                    infoAlertAssertion(it)  // should be Leave Alert
                }
                .expectNoEvent(Duration.ofMillis(1000))
                .thenCancel()
                .verify()
    }
}