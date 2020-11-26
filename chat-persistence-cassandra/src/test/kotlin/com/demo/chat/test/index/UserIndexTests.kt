package com.demo.chat.test.index

import com.demo.chat.domain.User
import com.demo.chat.repository.cassandra.ChatUserHandleRepository
import com.demo.chat.service.IndexService
import com.demo.chat.service.UserIndexService
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
//class UserIndexTests : IndexTestBase<UUID, User<UUID>, Map<String, String>>(
//        Supplier { User.create(Key.funKey(UUID(10, 10)), "test-name", "test-handle", "localhost/test.jpg") },
//        Supplier { Key.funKey(UUID(10, 10)) },
//        Supplier { mapOf(Pair(MembershipIndexService.MEMBEROF, UUID(10, 10).toString())) }
//)
class UserIndexTests {

    @MockBean
    private lateinit var byHandleRepo: ChatUserHandleRepository<UUID>

    @MockBean
    private lateinit var cassandra: ReactiveCassandraTemplate

    lateinit var userIndex: UserIndexService<UUID, Map<String, String>>

    @BeforeEach
    fun setUp() {
        userIndex = UserIndexCassandra(byHandleRepo, cassandra)
    }

    fun getIndex(): IndexService<UUID, User<UUID>, Map<String, String>> = userIndex
}