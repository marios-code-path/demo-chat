package com.demo.chat.test.conflation

import com.demo.chat.domain.User
import com.demo.chat.service.conflate.KeyEnricherPersistenceStore
import com.demo.chat.test.TestUserSupplier
import com.demo.chat.test.persistence.PersistenceTestBase
import org.junit.jupiter.api.Disabled

@Disabled
class KeyEnricherPersistenceStoreTests(store: KeyEnricherPersistenceStore<String, User<String>>)
    : PersistenceTestBase<String, User<String>>(TestUserSupplier, store)