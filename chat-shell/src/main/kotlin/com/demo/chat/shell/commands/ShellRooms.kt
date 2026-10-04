package com.demo.chat.shell.commands

import com.demo.chat.config.CompositeServiceBeans
import com.demo.chat.domain.ByIdRequest
import com.demo.chat.domain.ByStringRequest
import com.demo.chat.domain.KeyInputException
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.TypeUtil
import com.demo.chat.security.rsocket.CoreAuthorizationRefusal
import com.demo.chat.security.rsocket.CoreNotFound
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

/**
 * A `--topic` value that names no room and is the id of no room.
 *
 * [refused] is true when the id lookup was refused. The server check cannot
 * place an id that the registry does not hold, so it refuses an unknown id as
 * it refuses a room that the caller may not read.
 */
class UnknownRoomException(val topic: String, val refused: Boolean = false) : RuntimeException(
    if (refused) "No room has the name or the id $topic, or you may not read that room."
    else "No room has the name or the id $topic."
)

/**
 * The one reader of a `--topic` value. See `CHAT-scoizkpm`.
 *
 * **Every room command takes a room name or a room id through one option.**
 * The shell reads the value as a name first. When no room has that name and
 * the value is a valid id, the shell reads the room by its id.
 *
 * **A name wins over an id.** A person types names, and a room id is a long
 * number that a name rarely equals.
 *
 * **Only a miss is a miss.** The server gives `NotFoundException` and
 * `KeyVerificationException` their own RSocket code, and the client decoder
 * makes `CoreNotFound` from it. Any other error reaches the caller unchanged.
 * So a store failure or a lost connection is not read as an unknown room.
 * Before, the shell read every `ApplicationErrorException` as a miss, and that
 * type carries every server failure.
 *
 * **An unknown id arrives as a refusal, not as a miss.** The `GET` check of
 * `getRoom` reads the root of the id before the service runs. An id that the
 * registry does not hold has no root, so `hasAccessToId` answers false. The
 * shell cannot tell that case from a room that the caller may not read, so the
 * message names both. Measured on 2026-10-03 with `hangup 999999999999`.
 *
 * A refusal of the name lookup reaches the caller as it arrived. That route
 * carries no check today. See `CHAT-dgjhljbl`.
 */
@Profile("shell")
@Component
class ShellRooms<T : Any>(
    compositeServices: CompositeServiceBeans<T, String>,
    private val typeUtil: TypeUtil<T>,
) {
    private val topicService = compositeServices.topicService()

    /** The room that [topic] names, or the room whose id is [topic]. */
    fun room(topic: String): MessageTopic<T> =
        topicService.getRoomByName(ByStringRequest(topic))
            .map<MessageTopic<T>> { it }
            .onErrorResume(CoreNotFound::class.java) { Mono.empty() }
            .switchIfEmpty(Mono.defer { byId(topic) })
            .switchIfEmpty(Mono.error(UnknownRoomException(topic)))
            .block()!!

    /** The id of the room that [topic] names. */
    fun idOf(topic: String): T = room(topic).key.id

    private fun byId(topic: String): Mono<MessageTopic<T>> {
        // `fromString` answers 0 for a text that is no number. `exactFrom` refuses it.
        val id = try {
            typeUtil.exactFrom(topic)
        } catch (_: KeyInputException) {
            return Mono.empty()
        }
        return topicService.getRoom(ByIdRequest(id))
            .map<MessageTopic<T>> { it }
            .onErrorResume(CoreNotFound::class.java) { Mono.empty() }
            .onErrorResume(CoreAuthorizationRefusal::class.java) { Mono.error(UnknownRoomException(topic, refused = true)) }
    }
}
