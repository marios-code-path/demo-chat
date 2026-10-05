package com.demo.chat.test.commands

import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.domain.*
import com.demo.chat.domain.knownkey.RootKeys
import com.demo.chat.security.rsocket.CoreNotFound
import com.demo.chat.security.rsocket.RSocketNotFound
import com.demo.chat.service.composite.ChatMessageService
import com.demo.chat.service.composite.ChatTopicService
import com.demo.chat.service.composite.ChatUserService
import com.demo.chat.shell.commands.PubSubCommands
import com.demo.chat.shell.commands.ShellRooms
import io.rsocket.exceptions.ApplicationErrorException
import io.rsocket.exceptions.CustomRSocketException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Suppress("UNCHECKED_CAST")
class PubSubMessagesTests {
    private val messages = mock(ChatMessageService::class.java) as ChatMessageService<Long, String>
    private val users = mock(ChatUserService::class.java) as ChatUserService<Long>
    private val topics = mock(ChatTopicService::class.java) as ChatTopicService<Long, String>
    private val beans = mock(CompositeServiceBeans::class.java) as CompositeServiceBeans<Long, String>
    private val time = Instant.parse("2001-02-03T04:05:06Z")

    private fun commands(): PubSubCommands<Long> {
        given(beans.messageService()).willReturn(messages)
        given(beans.userService()).willReturn(users)
        given(beans.topicService()).willReturn(topics)
        given(topics.getRoomByName(ByStringRequest("lobby")))
            .willReturn(Mono.just(MessageTopic.create(Key.of(5L, 6L), "lobby")))
        return PubSubCommands(beans, TypeUtil.LongUtil, RootKeys(), ShellRooms(beans, TypeUtil.LongUtil))
    }

    private fun message(id: Long, sender: Long, seconds: Long, text: String): Message<Long, String> =
        Message.create(SimpleMessageKey(id, 7L, sender, 5L, time.plusSeconds(seconds)), text, true)

    private fun stamp(seconds: Long): String = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
        .withZone(ZoneId.systemDefault()).format(time.plusSeconds(seconds))

    @Test
    fun `stored time orders messages and each sender resolves once`() {
        given(messages.listMessages(ByIdRequest(5L))).willReturn(Flux.just(
            message(1, 11, 120, "last"), message(3, 11, 0, "first"), message(2, 12, 60, "middle")
        ))
        given(users.findByUserId(ByIdRequest(11L))).willReturn(Mono.just(User.create(Key.of(11L, 8L), "Alice", "alice", "")))
        given(users.findByUserId(ByIdRequest(12L))).willReturn(Mono.just(User.create(Key.of(12L, 8L), "Bob", "bob", "")))

        assertThat(commands().messages("lobby").lines()).containsExactly(
            "${stamp(0)} | alice | first", "${stamp(60)} | bob | middle", "${stamp(120)} | alice | last"
        )
        verify(users, times(1)).findByUserId(ByIdRequest(11L))
        verify(users, times(1)).findByUserId(ByIdRequest(12L))
    }

    @Test
    fun `missing users display their key and additional lines align under text`() {
        given(messages.listMessages(ByIdRequest(5L))).willReturn(Flux.just(message(1, 99, 0, "one\ntwo\r\nthree")))
        given(users.findByUserId(ByIdRequest(99L))).willReturn(Mono.empty())
        val prefix = "${stamp(0)} | 99 | "

        assertThat(commands().messages("lobby")).isEqualTo(prefix + "one\n" + " ".repeat(prefix.length) + "two\n" + " ".repeat(prefix.length) + "three")
    }

    @Test
    fun `a missing sender registry entry displays its key`() {
        given(messages.listMessages(ByIdRequest(5L))).willReturn(Flux.just(message(1, 99, 0, "text")))
        given(users.findByUserId(ByIdRequest(99L))).willReturn(Mono.error(CoreNotFound(CustomRSocketException(RSocketNotFound.CODE, "Object not Found"))))

        assertThat(commands().messages("lobby")).isEqualTo("${stamp(0)} | 99 | text")
    }

    @Test
    fun `a sender service failure reaches the caller`() {
        given(messages.listMessages(ByIdRequest(5L))).willReturn(Flux.just(message(1, 99, 0, "text")))
        given(users.findByUserId(ByIdRequest(99L))).willReturn(Mono.error(ApplicationErrorException("User lookup failed.")))

        assertThatThrownBy { commands().messages("lobby") }
            .isInstanceOf(ApplicationErrorException::class.java).hasMessage("User lookup failed.")
    }
    @Test
    fun `limit selects the newest messages and preserves time order`() {
        given(messages.listMessages(ByIdRequest(5L))).willReturn(Flux.just(
            message(1, 11, 120, "last"), message(3, 11, 0, "first"), message(2, 11, 60, "middle")
        ))
        given(users.findByUserId(ByIdRequest(11L))).willReturn(Mono.empty())
        val command = commands()

        assertThat(command.messages("lobby", 2).lines()).containsExactly(
            "${stamp(60)} | 11 | middle", "${stamp(120)} | 11 | last"
        )
        assertThat(command.messages("lobby", 5).lines()).hasSize(3)
        verify(users, times(2)).findByUserId(ByIdRequest(11L))
    }

    @Test
    fun `invalid limits fail before a room or message lookup`() {
        val command = commands()
        listOf(0, -1).forEach { limit ->
            assertThatThrownBy { command.messages("lobby", limit) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage("messages needs a positive --limit.")
        }
        verifyNoInteractions(messages, users)
        verify(topics, never()).getRoomByName(ByStringRequest("lobby"))
    }

}
