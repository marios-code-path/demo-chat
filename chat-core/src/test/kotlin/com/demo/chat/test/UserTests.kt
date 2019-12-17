package com.demo.chat.test

import com.demo.chat.domain.Key
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.User
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import java.util.*
import java.util.stream.Stream
import kotlin.random.Random

class UserTests : TestBase() {

    @Test
    fun `Should serialize deserialize JSON from to Any User`() {
        val topicJsons = ArrayList<String>()
        val topics = ArrayList<MessageTopic<out Any>>()

        Stream.generate { User.create(Key.anyKey(Random.nextInt()), "Test-Topic-R") }.limit(5)
                .forEach { msg ->
                    topicJsons.add(mapper.writeValueAsString(msg))
                }
        Stream.generate { MessageTopic.create(Key.anyKey(randomAlphaNumeric(10)), "Test-Topic-Z") }.limit(5)
                .forEach { msg ->
                    topicJsons.add(mapper.writeValueAsString(msg))
                }
        Stream.generate { MessageTopic.create(Key.anyKey(UUID.randomUUID()), "Test-Topic-U") }.limit(5)
                .forEach { msg ->
                    topicJsons.add(mapper.writeValueAsString(msg))
                }

        topicJsons
                .forEach { json ->
                    val tree = mapper.readTree(json)
                    println(json)
                    if (tree.fieldNames().hasNext()) {
                        when (tree.fieldNames().next()) {
                            "Topic" -> {
                                topics.apply {
                                    val topic: MessageTopic<Any> = mapper.readValue(json)
                                    add(topic)
                                }
                            }
                            else -> {
                            }
                        }
                    }

                }

        Assertions.assertThat(topics)
                .`as`("A collection of topics is present")
                .isNotNull
                .isNotEmpty

        topics
                .forEach { topic ->
                    Assertions
                            .assertThat(topic)
                            .`as`("Is a Topic")
                            .isNotNull
                            .hasFieldOrProperty("key")
                            .hasFieldOrProperty("data")
                }
    }
}