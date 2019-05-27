package com.demo.chat

import com.demo.chat.domain.ChatUserKey
import com.demo.chat.domain.RoomMember
import com.demo.chat.domain.RoomMemberships
import com.demo.chat.repository.cassandra.ChatRoomNameRepository
import com.demo.chat.repository.cassandra.ChatRoomRepository
import com.demo.chat.repository.cassandra.ChatUserHandleRepository
import com.demo.chat.repository.cassandra.ChatUserRepository
import com.demo.chat.service.ChatRoomServiceCassandra
import com.demo.chat.service.ChatUserServiceCassandra
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.rsocket.*
import io.rsocket.transport.netty.server.CloseableChannel
import io.rsocket.transport.netty.server.WebsocketServerTransport
import io.rsocket.util.DefaultPayload
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@SpringBootApplication
class ChatServiceRsocketApplication

fun main(args: Array<String>) {

    runApplication<ChatServiceRsocketApplication>(*args)
}

@Configuration
class ChatServiceModule {
    @Bean
    fun userService(userRepo: ChatUserRepository,
                    userHandleRepo: ChatUserHandleRepository): ChatUserServiceCassandra =
            ChatUserServiceCassandra(userRepo, userHandleRepo)

    @Bean
    fun roomService(roomRepo: ChatRoomRepository,
                    roomNameRepo: ChatRoomNameRepository): ChatRoomServiceCassandra =
            ChatRoomServiceCassandra(roomRepo)
}

@Controller
class RoomController(val roomService: ChatRoomServiceCassandra,
                     val userService: ChatUserServiceCassandra) {
    val logger: Logger = LoggerFactory.getLogger(this::class.simpleName)

    @MessageMapping("room-create")
    fun createRoom(req: RoomCreateRequest): Mono<RoomCreateResponse> =
            roomService
                    .createRoom(req.roomName)
                    .map {
                        RoomCreateResponse(it)
                    }

    @MessageMapping("room-delete")
    fun deleteRoom(req: RoomRequest): Mono<Void> =
            roomService
                    .deleteRoom(req.roomId)

    @MessageMapping("room-list")
    fun listRooms(v: Void): Flux<RoomResponse> =
            roomService
                    .getRooms(true)
                    .map {
                        RoomResponse(it)
                    }

    @MessageMapping("room-id")
    fun getRoom(req: RoomRequest): Mono<RoomResponse> =
            roomService
                    .getRoomById(req.roomId)
                    .map {
                        RoomResponse(it)
                    }

    @MessageMapping("room-join")
    fun joinRoom(req: RoomJoinRequest): Mono<Void> =
            roomService
                    .joinRoom(req.uid, req.roomId)

    @MessageMapping("room-leave")
    fun leaveRoom(req: RoomLeaveRequest): Mono<Void> =
            roomService
                    .leaveRoom(req.uid, req.roomId)

    @MessageMapping("room-members")
    fun roomMembers(req: RoomRequest): Mono<RoomMemberships> = roomService
            .roomMembers(req.roomId)
            .flatMap { members ->
                userService // kludge! This is nullable ! make sure enforce non-nullability across service contract
                        .getUsersById(Flux.fromStream(members.stream()))
                        .map { u ->
                            RoomMember(u.key.userId, u.key.handle)
                        }
                        .collectList()
                        .map { memberList ->
                            RoomMemberships(memberList.toSet())
                        }
            }
}

@Controller
class UserController(val userService: ChatUserServiceCassandra) {
    val logger: Logger = LoggerFactory.getLogger(this::class.simpleName)

    @MessageMapping("user-create")
    fun createNewUser(userReq: UserCreateRequest): Mono<UserCreateResponse> {
        return userService.createUser(userReq.name, userReq.userHandle)
                .map {
                    UserCreateResponse(
                            ChatUserKey(it.userId, it.handle))
                }
    }

    @MessageMapping("user-handle")
    fun findByHandle(userReq: UserRequest): Mono<UserResponse> {
        return userService.getUser(userReq.userHandle)
                .map {
                    logger.info("The user is: $it")
                    UserResponse(it!!)
                }
    }

    @MessageMapping("user-id")
    fun findByUserId(userReq: UserRequestId): Mono<UserResponse> {
        return userService.getUserById(userReq.userId)
                .map {
                    UserResponse(it)
                }
    }

    @MessageMapping("user-id-list")
    fun findByUserIdList(userReq: UserRequestIdList): Flux<UserResponse> {
        return userService.getUsersById(userReq.userId)
                .map {
                    UserResponse(it)
                }
    }
}

@Deprecated("Use the Controller from now on.")
class ChatRsocketUserServiceRunnable(val userService: ChatUserServiceCassandra) {
    val logger: Logger = LoggerFactory.getLogger("UserRSocket")

    val port = 9191
    val mapper = ObjectMapper().registerModule(KotlinModule()).apply {
        propertyNamingStrategy = PropertyNamingStrategy.LOWER_CAMEL_CASE
        setSerializationInclusion(JsonInclude.Include.NON_NULL)
        configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        //configure(SerializationFeature.WRAP_ROOT_VALUE, true)
    }.findAndRegisterModules()!!

    val closeable: Mono<CloseableChannel> = RSocketFactory
            .receive()
            .acceptor { setup, rSocket ->
                handler(setup, rSocket)
            } // server handler RSocket
            .transport(WebsocketServerTransport.create(port))  // Netty websocket transport
            .start()

    private fun handler(setup: ConnectionSetupPayload, rSocket: RSocket): Mono<RSocket> {
        return Mono.just(object : AbstractRSocket() {
            override fun requestResponse(payload: Payload): Mono<Payload> {
                val userHandle = payload?.dataUtf8
                println("userHandle :$userHandle")

                return Mono.just(DefaultPayload.create("Return data"))
            }
        })
    }

    @Bean
    fun startRSocket(): ApplicationRunner = ApplicationRunner {
        logger.info("START")
        closeable.block()
        logger.warn("STOPPED")
    }
}