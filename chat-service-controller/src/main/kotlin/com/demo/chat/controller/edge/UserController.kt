package com.demo.chat.controller.edge

import com.demo.chat.ByHandleRequest
import com.demo.chat.ByIdRequest
import com.demo.chat.UserCreateRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.service.UserIndexService
import com.demo.chat.service.UserPersistence
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.messaging.handler.annotation.MessageMapping
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.function.Function

open class UserController<T, Q>(
        val userPersistence: UserPersistence<T>,
        private val userIndex: UserIndexService<T, Q>,
        private val userHandleToQuery: Function<ByHandleRequest, Q>,
) {
    val logger: Logger = LoggerFactory.getLogger(this::class.simpleName)

    @MessageMapping("user-add")
    fun addUser(userReq: UserCreateRequest): Mono<T> =
            userPersistence
                    .key()
                    .flatMap {
                        val user = User.create(
                                Key.funKey(it.id),
                                userReq.name,
                                userReq.handle,
                                userReq.imgUri
                        )
                        Flux.concat(
                                userPersistence.add(user),
                                userIndex.add(user)
                        )
                                .then(Mono.just(it.id))
                    }

    @MessageMapping("user-by-handle")
    fun findByHandle(req: ByHandleRequest): Flux<out User<T>> = userIndex
            .findBy(userHandleToQuery.apply(req)) //mapOf(Pair(HANDLE, byHandleReq.handle)))
            .flatMap(userPersistence::get)

    @MessageMapping("user-by-id")
    fun findByUserId(req: ByIdRequest<T>): Mono<out User<T>> = userPersistence
            .get(Key.funKey(req.id))

//    @MessageMapping("user-by-ids")
//    fun findByUserIdList(userReq: Flux<UserRequestId>): Flux<out User> =
//            userPersistence.findByIds(userReq
//                    .map {
//                        it.userId
//                    })
}