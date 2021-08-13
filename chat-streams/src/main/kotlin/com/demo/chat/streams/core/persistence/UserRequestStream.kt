package com.demo.chat.streams.core.persistence

import com.demo.chat.domain.User
import com.demo.chat.service.IndexService
import com.demo.chat.service.PersistenceStore
import com.demo.chat.streams.core.UserCreateRequest
import org.springframework.context.annotation.Bean
import reactor.core.publisher.Flux
import java.util.function.Function

open class UserRequestStream<T, Q>(
    private val userPersistence: PersistenceStore<T, User<T>>,
    private val userIndex: IndexService<T, User<T>, Q>
) {
    @Bean
    fun receiveUserRequest() = Function<Flux<UserCreateRequest>, Flux<User<T>>> { userReq ->
        userReq.flatMap { req ->
            userPersistence.key()
                .map { key ->
                    User.create(key, req.name, req.handle, req.imgUri)
                }
                .flatMap { user ->
                    userIndex
                        .add(user)
                        .map { user }
                }
        }
    }
}