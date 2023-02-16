package com.demo.chat.test.init

import com.demo.chat.controller.composite.UserServiceController
import com.demo.chat.controller.core.KeyServiceController
import com.demo.chat.controller.core.PersistenceServiceController
import com.demo.chat.domain.AuthMetadata
import com.demo.chat.domain.User
import com.demo.chat.service.core.IndexService
import com.demo.chat.service.core.PersistenceStore
import com.demo.chat.service.core.UserIndexService
import com.demo.chat.service.core.UserPersistence
import com.demo.chat.service.dummy.DummyIndexService
import com.demo.chat.service.dummy.DummyKeyService
import com.demo.chat.service.dummy.DummyPersistenceStore
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller

@TestConfiguration
class TestDummyControllerServices {

    class DummyUserIndex<T, Q>(that: IndexService<T, User<T>, Q>) : UserIndexService<T, Q>,
        IndexService<T, User<T>, Q> by that

    class DummyUserPersistence<T>(that: PersistenceStore<T, User<T>>) : UserPersistence<T>,
        PersistenceStore<T, User<T>> by that

    @Controller
    @MessageMapping("key")
    class TestKeyController<T> : KeyServiceController<T>(DummyKeyService())

    @Controller
    @MessageMapping("user")
    class TestUserController<T> : UserServiceController<T, Map<String, String>>(
        DummyUserPersistence(DummyPersistenceStore()),
        DummyUserIndex(DummyIndexService()),
        java.util.function.Function { i -> mapOf(Pair(UserIndexService.HANDLE, i.handle)) })

    class DummyAuthMetadataPersistence<T>(that: PersistenceStore<T, AuthMetadata<T>>) :
        PersistenceStore<T, AuthMetadata<T>> by that

    @Controller
    @MessageMapping("persist.authmetadata")
    class AuthMetaPersistenceController<T> :
        PersistenceServiceController<T, AuthMetadata<T>>(DummyAuthMetadataPersistence(DummyPersistenceStore()))
}