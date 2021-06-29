package com.demo.chat.service.impl.memory.persistence

import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.service.IKeyService
import java.util.function.Function

open class UserPersistenceInMemory<T>(
        keyService: IKeyService<T>,
        keyReceiver: Function<User<T>, Key<T>>
)
    : InMemoryPersistence<T, User<T>>(keyService, User::class.java, keyReceiver)