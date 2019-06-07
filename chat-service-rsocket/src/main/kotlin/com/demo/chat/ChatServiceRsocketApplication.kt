package com.demo.chat

import com.demo.chat.domain.*
import com.demo.chat.repository.cassandra.*
import com.demo.chat.service.*
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.FilterType

@SpringBootApplication
@ComponentScan(excludeFilters = [
    ComponentScan.Filter(type = FilterType.ANNOTATION, value = [ExcludeFromTests::class])
])
class ChatServiceRsocketApplication

fun main(args: Array<String>) {

    runApplication<ChatServiceRsocketApplication>(*args)
}

annotation class ExcludeFromTests

@Configuration
@ExcludeFromTests
class ChatServiceModule {
    @Bean
    fun userService(userRepo: ChatUserRepository,
                    userHandleRepo: ChatUserHandleRepository): ChatUserService<out User<UserKey>, UserKey> =
            ChatUserServiceCassandra(userRepo, userHandleRepo)

    @Bean
    fun roomService(roomRepo: ChatRoomRepository,
                    roomNameRepo: ChatRoomNameRepository): ChatRoomService<out Room<RoomKey>, RoomKey> =
            ChatRoomServiceCassandra(roomRepo)

    @Bean
    fun messagesService(messageRepo: ChatMessageRepository,
                        messageRoomRepo: ChatMessageRoomRepository): ChatMessageService<out TextMessage, MessageKey> =
            ChatMessageServiceCassandra(messageRepo, messageRoomRepo)
}