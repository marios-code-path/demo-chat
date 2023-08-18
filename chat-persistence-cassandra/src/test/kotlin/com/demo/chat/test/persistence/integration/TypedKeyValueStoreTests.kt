package com.demo.chat.test.persistence.integration

import com.demo.chat.domain.Key
import com.demo.chat.domain.KeyValuePair
import com.demo.chat.persistence.cassandra.impl.KeyServiceCassandra
import com.demo.chat.persistence.cassandra.impl.KeyValuePersistenceCassandra
import com.demo.chat.persistence.cassandra.repository.KeyValuePairRepository
import com.demo.chat.test.TestLongKeyService
import com.demo.chat.test.persistence.KeyValueStoreTestBase
import com.demo.chat.test.repository.RepositoryTestConfiguration
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.util.function.Supplier

@ExtendWith(SpringExtension::class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = [RepositoryTestConfiguration::class]
)
@TestPropertySource(properties = ["app.service.core.kv", "app.key.type=long"])
class TypedKeyValueStoreTests : KeyValueStoreTestBase<Long, Any> {

    @Autowired
    constructor(
        repo: KeyValuePairRepository<Long>,
        mapper: ObjectMapper,
        template: ReactiveCassandraTemplate
    ) : super(
        Supplier { KeyValuePair.create(Key.funKey(1L), "TEST") },
        Supplier { String::class.java },
        KeyValuePersistenceCassandra(
            KeyServiceCassandra(template, TestLongKeyService()), repo, mapper
        ),
        { k -> k.key })
}