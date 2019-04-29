package com.demo.chatevents

import com.demo.chat.domain.*
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import java.time.Instant
import java.util.*


// Variances of Keys we want
data class TestAlertMessageKey(
        override val id: UUID,
        override val roomId: UUID,
        override val timestamp: Instant
) : AlertMessageKey

data class TestTextMessageKey(
        override val id: UUID,
        override val userId: UUID,
        override val roomId: UUID,
        override val timestamp: Instant
) : TextMessageKey

data class TestTextMessage(
        override val key: TestTextMessageKey,
        override val value: String,
        override val visible: Boolean
) : TextMessage

data class TestInfoAlert(
        override val key: TestAlertMessageKey,
        override val value: RoomInfo,
        override val visible: Boolean
) : InfoAlert


data class TestLeaveAlert(
        override val key: TestAlertMessageKey,
        override val value: UUID,
        override val visible: Boolean
) : LeaveAlert

data class TestJoinAlert(
        override val key: TestAlertMessageKey,
        override val value: UUID,
        override val visible: Boolean
) : JoinAlert

fun textMessageAssertion(msg: TextMessage) = { println(msg)
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