package com.demo.chat.service.impl.memory.persistence

import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.service.IKeyService
import java.util.function.Function

class UserPersistenceInMemory<T>(
        keyService: IKeyService<T>,
        classId: Class<*>,
        keyReceiver: Function<User<T>, Key<T>>
)
    : InMemoryPersistence<T, User<T>>(keyService, classId, keyReceiver)