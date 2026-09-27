package com.demo.chat.persistence.memory.impl

import com.demo.chat.domain.knownkey.RootKeys

import com.demo.chat.domain.knownkey.ChatDomain
import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.domain.comparator.UserComparater
import com.demo.chat.service.core.IKeyService
import com.demo.chat.service.core.UserPersistence
import java.util.function.Function

open class UserPersistenceInMemory<T>(
    keyService: IKeyService<T>,
    rootKeys: RootKeys<T>,
    keyFromEntity: Function<User<T>, Key<T>>
) : ComparatorInMemoryPersistence<T, User<T>>(keyService, ChatDomain.USER, rootKeys, keyFromEntity, UserComparater()),
    UserPersistence<T>