package com.demo.chat.test.domain

import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.test.TestBase
import com.demo.chat.test.TestUser
import com.demo.chat.test.randomAlphaNumeric
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import java.util.*
import java.util.stream.Stream
import kotlin.random.Random

class UserTests : TestBase() {

    @Test
    fun `Any User should serialize deserialize`() {
        val topicJsons = ArrayList<String>()
        val topics = ArrayList<User<out Any>>()

        Stream.generate { User.create(Key.anyKey(Random.nextInt()), "name1", "handle1", "uri1") }.limit(1)
                .forEach { msg ->
                    topicJsons.add(mapper.writeValueAsString(msg))
                }
        Stream.generate { User.create(Key.anyKey(randomAlphaNumeric(10)), "name2", "handle2", "uri2") }.limit(1)
                .forEach { msg ->
                    topicJsons.add(mapper.writeValueAsString(msg))
                }
        Stream.generate { User.create(Key.anyKey(UUID.randomUUID()), "name3", "handle3", "uri3") }.limit(1)
                .forEach { msg ->
                    topicJsons.add(mapper.writeValueAsString(msg))
                }

        topicJsons
                .forEach { json ->
                    val tree = mapper.readTree(json)

                    if (tree.fieldNames().hasNext()) {
                        when (tree.fieldNames().next()) {
                            "User" -> {
                                topics.apply {
                                    val topic = mapper.readValue<TestUser<out Any>>(json)
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
                .forEach { user ->
                    Assertions
                            .assertThat(user)
                            .`as`("Is a User")
                            .isNotNull
                            .hasFieldOrProperty("key")
                            .hasFieldOrProperty("handle")
                            .hasFieldOrProperty("name")
                }
    }
}