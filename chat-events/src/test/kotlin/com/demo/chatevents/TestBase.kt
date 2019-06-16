package com.demo.chatevents

import com.demo.chat.domain.TextMessage
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import java.util.*

fun textMessageAssertion(msg: TextMessage) {
    MatcherAssert
            .assertThat("A Text Message should have property state", msg,
                    Matchers.allOf(
                            Matchers.notNullValue(),
                            Matchers.hasProperty("value", Matchers.not(Matchers.isEmptyOrNullString())),
                            Matchers.hasProperty("key",
                                    Matchers
                                            .allOf(Matchers.notNullValue(),
                                                    Matchers.hasProperty("userId"),
                                                    Matchers.hasProperty("roomId"))
                            ))
            )
}

fun testRoomId() = UUID.fromString("ecb2cb88-5dd1-44c3-b818-301000000000")//UUID.randomUUID()

fun testUserId() = UUID.fromString("ecb2cb88-5dd1-44c3-b818-133730000000")

fun randomUserId(): UUID {
    val lastDigit = Integer.toHexString(Random().nextInt(16))
    return UUID.fromString("ecb2cb88-5dd1-44c3-b818-13373000000$lastDigit")
}

fun randomText() =
        "Text ${Random().nextLong()}"