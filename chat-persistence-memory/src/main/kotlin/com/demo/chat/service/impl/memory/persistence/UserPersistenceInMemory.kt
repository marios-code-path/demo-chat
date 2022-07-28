package com.demo.chat.service.impl.memory.persistence

import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.domain.comparator.UserComparater
import com.demo.chat.service.IKeyService
import com.demo.chat.service.UserPersistence
import java.util.function.Function

open class UserPersistenceInMemory<T>(
    keyService: IKeyService<T>,
    keyFromEntity: Function<User<T>, Key<T>>
) : ComparatorInMemoryPersistence<T, User<T>>(keyService, User::class.java, keyFromEntity, UserComparater()),
    UserPersistence<T>