package com.demo.chat.service.composite.impl

import com.demo.chat.domain.*
import com.demo.chat.service.composite.ChatUserService
import com.demo.chat.service.core.UserIndexService
import com.demo.chat.service.core.UserPersistence
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.function.Function
import java.util.stream.Collectors

open class UserServiceImpl<T, Q>(
    val userPersistence: UserPersistence<T>,
    private val userIndex: UserIndexService<T, Q>,
    private val userHandleToQuery: Function<ByStringRequest, Q>,
) : ChatUserService<T> {

    val logger: Logger = LoggerFactory.getLogger(this::class.simpleName)

    override fun addUser(userReq: UserCreateRequest): Mono<out Key<T>> =
        findByUsername(ByStringRequest(userReq.handle))
            .doOnNext { throw DuplicateException }
            .then (
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
                            .then(Mono.just(it))
                    }
            )

    override fun findByUsername(req: ByStringRequest): Flux<out User<T>> = userIndex
        .findBy(userHandleToQuery.apply(req))
        .flatMap(userPersistence::get)

    override fun findByUserId(req: ByIdRequest<T>): Mono<out User<T>> = userPersistence
        .get(Key.funKey(req.id))

    fun findByUserIdList(userReq: List<ByIdRequest<T>>): Flux<out User<T>> =
        userPersistence.byIds(
            userReq
                .stream()
                .map { Key.funKey(it.id) }
                .collect(Collectors.toList())
        )
}