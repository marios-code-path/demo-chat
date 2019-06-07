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
        override val value: RoomMetaData,
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


class TestClosingKey(override val roomId: UUID) : AlertMessageKey {
    override val id: UUID
        get() = UUID.fromString("ecb2cb88-5dd1-44c3-b818-defa0000000")
    override val timestamp: Instant
        get() = Instant.now()
}

class TestClosingAlert(override val key: AlertMessageKey) : ClosingAlert {
    override val value: UUID
        get() = UUID.fromString("ecb2cb88-5dd1-44c3-b818-defa1111111")
    override val visible: Boolean
        get() = false
}


fun testRoomId() = UUID.fromString("ecb2cb88-5dd1-44c3-b818-301000000000")//UUID.randomUUID()

fun testUserId() = UUID.fromString("ecb2cb88-5dd1-44c3-b818-133730000000")

fun randomUserId(): UUID {
    val lastDigit = Integer.toHexString(Random().nextInt(16))
    return UUID.fromString("ecb2cb88-5dd1-44c3-b818-13373000000$lastDigit")
}

fun randomText() =
        "Text ${Random().nextLong()}"