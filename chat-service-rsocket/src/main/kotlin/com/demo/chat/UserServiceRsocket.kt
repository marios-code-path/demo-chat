package com.demo.chat

import com.demo.chat.domain.Message
import com.demo.chat.domain.MessageKey
import com.demo.chat.service.ChatMessageService
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*


class UserServiceRsocket<MK : MessageKey, M : Message<MK, Any>>(
        val msgService: ChatMessageService<MK, M>
) : ChatMessageService<MK, M> {

    override fun getMessage(id: UUID): Mono<M> = msgService
            .getMessage(id)

    override fun getTopicMessages(roomId: UUID): Flux<M> = msgService
            .getTopicMessages(roomId)

    override fun storeMessage(uid: UUID, roomId: UUID, messageText: String)
            : Mono<MK> = msgService
            .storeMessage(uid, roomId, messageText)
}