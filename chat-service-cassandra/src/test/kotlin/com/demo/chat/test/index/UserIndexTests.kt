package com.demo.chat.test.index

import com.demo.chat.repository.cassandra.ChatUserHandleRepository
import com.demo.chat.service.UserIndexService
import com.demo.chat.service.index.UserCriteriaCodec
import com.demo.chat.service.index.UserIndexCassandra
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.util.*


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(SpringExtension::class)
class UserIndexTests {

    @MockBean
    private lateinit var byHandleRepo: ChatUserHandleRepository<UUID>

    @MockBean
    private lateinit var cassandra: ReactiveCassandraTemplate

    lateinit var userIndex: UserIndexService<UUID>

    @BeforeEach
    fun setUp() {
        userIndex = UserIndexCassandra(UserCriteriaCodec(), byHandleRepo, cassandra)
    }
}