package com.demo.chat.test.conflation

import com.demo.chat.domain.User
import com.demo.chat.service.impl.memory.persistence.UserPersistenceInMemory
import com.demo.chat.test.TestKeyService
import com.demo.chat.test.TestUserSupplier
import com.demo.chat.test.persistence.PersistenceTestBase

class IndexedPersistenceTests : PersistenceTestBase<String, User<String>>
(TestUserSupplier, UserPersistenceInMemory(TestKeyService(), User::class.java) { t -> t.key })