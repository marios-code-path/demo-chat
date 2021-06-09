package com.demo.chat.test.conflation

import com.demo.chat.domain.User
import com.demo.chat.service.conflate.KeyFirstPersistence
import com.demo.chat.test.TestUserSupplier
import com.demo.chat.test.key.MockKeyServiceResolver
import com.demo.chat.test.persistence.PersistenceTestBase
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.extension.ExtendWith

@Disabled
class KeyFirstPersistenceTests(store: KeyFirstPersistence<String, User<String>>)
    : PersistenceTestBase<String, User<String>>(TestUserSupplier, store)