package com.demo.chat.streams.core.persistence

import com.demo.chat.domain.User
import com.demo.chat.service.EnricherPersistenceStore
import com.demo.chat.service.IndexService
import com.demo.chat.service.PersistenceStore
import com.demo.chat.streams.core.UserCreateRequest
import org.springframework.context.annotation.Bean
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.function.Function

open class UserRequestStream<T, Q>(
    private val userPersistence: EnricherPersistenceStore<T, UserCreateRequest, User<T>>,
    private val userIndex: IndexService<T, User<T>, Q>
) {
    @Bean
    open fun receiveUserRequest() = Function<Flux<UserCreateRequest>, Flux<User<T>>> { userReq ->
        userReq
            .flatMap { req ->
                userPersistence.addEnriched(req)
            }
            .flatMap { user ->
                userIndex
                    .add(user)
                    .then(Mono.just(user))
            }
    }
}