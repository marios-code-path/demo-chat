package com.demo.chat.test.conflation

import com.demo.chat.domain.User
import com.demo.chat.service.conflate.KeyEnricherPersistenceStore
import com.demo.chat.test.TestUserSupplier
import com.demo.chat.test.index.MockIndexResolver
import com.demo.chat.test.persistence.MockPersistenceResolver
import com.demo.chat.test.persistence.PersistenceTestBase
import org.junit.jupiter.api.extension.ExtendWith

data class TestUserRequest(val name: String, val handle: String, val uri: String)

//@Disabled
@ExtendWith(MockPersistenceResolver::class, MockIndexResolver::class)
class KeyEnricherPersistenceStoreTests(store: KeyEnricherPersistenceStore<String, TestUserRequest, User<String>>)
    : PersistenceTestBase<String, User<String>>(TestUserSupplier, store)