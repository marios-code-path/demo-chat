package com.demo.chat.test.conflation

import com.demo.chat.domain.User
import com.demo.chat.service.conflate.KeyFirstPersistence
import com.demo.chat.test.TestUserSupplier
import com.demo.chat.test.persistence.PersistenceTestBase

class KeyFirstPersistenceTests(store: KeyFirstPersistence<String, User<String>>)
    : PersistenceTestBase<String, User<String>>(TestUserSupplier, store)