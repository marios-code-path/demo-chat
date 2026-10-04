package com.demo.chat.test.commands

import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.domain.ByIdRequest
import com.demo.chat.domain.ByStringRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.TypeUtil
import com.demo.chat.security.rsocket.CoreAuthorizationRefusal
import com.demo.chat.security.rsocket.CoreNotFound
import com.demo.chat.security.rsocket.RSocketNotFound
import com.demo.chat.service.composite.ChatTopicService
import com.demo.chat.shell.commands.ShellRooms
import com.demo.chat.shell.commands.UnknownRoomException
import io.rsocket.exceptions.ApplicationErrorException
import io.rsocket.exceptions.CustomRSocketException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import reactor.core.publisher.Mono

/**
 * One `--topic` value names a room by its name or by its id. See
 * `CHAT-scoizkpm`.
 *
 * Both lookups answer a miss as `CoreNotFound`. The client decoder makes it
 * from the RSocket code that the server gives `NotFoundException` and
 * `KeyVerificationException`. Any other server failure arrives as
 * `ApplicationErrorException`, and it is not a miss. A security refusal is `CoreAuthorizationRefusal`,
 * which the client decoder makes from the RSocket code.
 */
@Suppress("UNCHECKED_CAST")
class ShellRoomsTests {

    private val topics = mock(ChatTopicService::class.java) as ChatTopicService<Long, String>
    private val beans = mock(CompositeServiceBeans::class.java) as CompositeServiceBeans<Long, String>

    private fun rooms(): ShellRooms<Long> {
        given(beans.topicService()).willReturn(topics)
        return ShellRooms(beans, TypeUtil.LongUtil)
    }

    private val lobby = MessageTopic.create(Key.of(1555400854075346944L, 5L), "lobby")
    private val miss = CoreNotFound(CustomRSocketException(RSocketNotFound.CODE, "Object not Found"))

    @Test
    fun `a name finds its room with no id lookup`() {
        given(topics.getRoomByName(ByStringRequest("lobby"))).willReturn(Mono.just(lobby))

        assertThat(rooms().idOf("lobby")).isEqualTo(1555400854075346944L)
        verify(topics, never()).getRoom(any() ?: ByIdRequest(0L))
    }

    @Test
    fun `an id finds its room when no room has that name`() {
        given(topics.getRoomByName(ByStringRequest("1555400854075346944"))).willReturn(Mono.error(miss))
        given(topics.getRoom(ByIdRequest(1555400854075346944L))).willReturn(Mono.just(lobby))

        assertThat(rooms().room("1555400854075346944").data).isEqualTo("lobby")
    }

    /** **A text that is no id does not reach the id lookup.** `fromString` would read it as 0. */
    @Test
    fun `an unknown name that is no id is refused with one message`() {
        given(topics.getRoomByName(ByStringRequest("hall"))).willReturn(Mono.error(miss))

        assertThatThrownBy { rooms().idOf("hall") }
            .isInstanceOf(UnknownRoomException::class.java)
            .hasMessage("No room has the name or the id hall.")
        verify(topics, never()).getRoom(any() ?: ByIdRequest(0L))
    }

    @Test
    fun `an unknown id is refused with the same message`() {
        given(topics.getRoomByName(ByStringRequest("99"))).willReturn(Mono.error(miss))
        given(topics.getRoom(ByIdRequest(99L))).willReturn(Mono.error(CoreNotFound(CustomRSocketException(RSocketNotFound.CODE, "Key 99 is not in the registry."))))

        assertThatThrownBy { rooms().idOf("99") }
            .isInstanceOf(UnknownRoomException::class.java)
            .hasMessage("No room has the name or the id 99.")
    }

    /**
     * **A refused id lookup names both causes.** The server refuses an id that
     * the registry does not hold, as it refuses a room the caller may not read.
     */
    @Test
    fun `a refused id lookup reports an unknown or unreadable room`() {
        given(topics.getRoomByName(ByStringRequest("99"))).willReturn(Mono.error(miss))
        given(topics.getRoom(ByIdRequest(99L)))
            .willReturn(Mono.error(CoreAuthorizationRefusal(CustomRSocketException(0x403, "Access Denied"))))

        assertThatThrownBy { rooms().idOf("99") }
            .isInstanceOf(UnknownRoomException::class.java)
            .hasMessage("No room has the name or the id 99, or you may not read that room.")
    }

    /** **A refusal of the name lookup is not a miss.** It reaches the caller as it arrived. */
    @Test
    fun `a refused name lookup reaches the caller`() {
        given(topics.getRoomByName(ByStringRequest("lobby")))
            .willReturn(Mono.error(CoreAuthorizationRefusal(CustomRSocketException(0x403, "Access Denied"))))

        assertThatThrownBy { rooms().idOf("lobby") }
            .isInstanceOf(CoreAuthorizationRefusal::class.java)
            .hasMessage("Access Denied")
        verify(topics, never()).getRoom(any() ?: ByIdRequest(0L))
    }

    /**
     * **A server failure is not a miss.** It carries the generic code `0x201`.
     * Before, the shell read it as an unknown room and hid the failure.
     */
    @Test
    fun `a failure of the name lookup reaches the caller`() {
        given(topics.getRoomByName(ByStringRequest("lobby")))
            .willReturn(Mono.error(ApplicationErrorException("Query timed out after PT2S")))

        assertThatThrownBy { rooms().idOf("lobby") }
            .isInstanceOf(ApplicationErrorException::class.java)
            .hasMessage("Query timed out after PT2S")
        verify(topics, never()).getRoom(any() ?: ByIdRequest(0L))
    }

    @Test
    fun `a failure of the id lookup reaches the caller`() {
        given(topics.getRoomByName(ByStringRequest("99"))).willReturn(Mono.error(miss))
        given(topics.getRoom(ByIdRequest(99L)))
            .willReturn(Mono.error(ApplicationErrorException("Query timed out after PT2S")))

        assertThatThrownBy { rooms().idOf("99") }
            .isInstanceOf(ApplicationErrorException::class.java)
            .hasMessage("Query timed out after PT2S")
    }
}
