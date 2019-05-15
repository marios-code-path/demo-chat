package com.demo.chat.edge

import com.demo.chat.domain.*
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.mockito.Mockito
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

object TestUtil

fun <T> anyObject(): T {
    Mockito.anyObject<T>()
    return uninitialized()
}

fun <T> uninitialized(): T = null as T

fun userAssertion(user: User<UserKey>) {
    MatcherAssert
            .assertThat("A User has key and properties", user,
                    Matchers.allOf(
                            Matchers.notNullValue(),
                            Matchers.hasProperty("name", Matchers.not(Matchers.isEmptyOrNullString())),
                            Matchers.hasProperty("key",
                                    Matchers
                                            .allOf(
                                                    Matchers.notNullValue(),
                                                    Matchers.hasProperty("handle"),
                                                    Matchers.hasProperty("userId")
                                            )
                            )
                    ))
}

fun userKeyAssertions(key: UserKey) {
    MatcherAssert
            .assertThat("A User has key and properties", key,
                    Matchers.allOf(
                            Matchers.notNullValue(),
                            Matchers.hasProperty("handle", Matchers.not(Matchers.isEmptyOrNullString())),
                            Matchers.hasProperty("userId", Matchers.not(Matchers.isEmptyOrNullString())),
                    ))
}

fun infoAlertAssertion(msg: Message<MessageKey, Any>) {
    when(msg) {
        is InfoAlert -> {
            genericAlertTypeAssertion(msg)
        }
        is ClosingAlert -> {
            genericAlertTypeAssertion(msg)
        }
        else -> {
            AssertionError("The first message was not an alert!")
        }
    }

}

fun genericAlertTypeAssertion(msg: Message<MessageKey, Any>) {
    MatcherAssert
            .assertThat("Message for Room Info Received", msg,
                    Matchers.allOf(
                            Matchers.notNullValue(),
                            Matchers.hasProperty("value"),
                            Matchers.hasProperty("key")
                    )
            )
}

fun roomAssertion(room: Room<RoomKey>) {
    MatcherAssert
            .assertThat("A Room has key and properties", room,
                    Matchers.allOf(
                            Matchers.notNullValue(),
                            Matchers.hasProperty("members", Matchers.notNullValue()),
                            Matchers.hasProperty("key",
                                    Matchers
                                            .allOf(
                                                    Matchers.notNullValue(),
                                                    Matchers.hasProperty("name"),
                                                    Matchers.hasProperty("roomId")
                                            )
                            )
                    ))
}

fun textMessageAssertion(msg: TextMessage) = {
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
