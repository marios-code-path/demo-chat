package com.demo.chat.edge

import com.demo.chat.domain.*
import com.demo.chat.service.ChatService
import com.demo.chat.service.ChatTopicService
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.util.*

@Service
class ChatUserEdgeService(val backend: ChatService<Room<RoomKey>, User<UserKey>, Message<TextMessageKey, Any>>,
                          val topicService: ChatTopicService) : ChatUserEdge {
    override fun createUser(name: String, handle: String): Mono<UserKey> =
        backend
                .storeUser(
                        name, handle
                )


    override fun getUser(handle: String): Mono<UserKey> =
            backend
                    .getUserByHandle(handle)

    override fun createUserAuthentication(uid: UUID, password: String): Mono<Void> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun authenticateUser(name: String, password: String): Mono<User<UserKey>> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}