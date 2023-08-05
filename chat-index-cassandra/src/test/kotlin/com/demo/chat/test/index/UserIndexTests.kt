package com.demo.chat.test.index

import com.datastax.driver.core.utils.UUIDs
import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.index.cassandra.impl.UserIndex
import com.demo.chat.index.cassandra.repository.ChatUserHandleRepository
import com.demo.chat.service.core.IndexService
import com.demo.chat.service.core.UserIndexService
import com.demo.chat.test.IndexRepositoryTestConfiguration
import com.demo.chat.test.anyObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.data.cassandra.core.ReactiveCassandraTemplate
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.*


@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
//class UserIndexTests : IndexTestBase<UUID, User<UUID>, Map<String, String>>(
//        Supplier { User.create(Key.funKey(UUID(10, 10)), "test-name", "test-handle", "localhost/test.jpg") },
//        Supplier { Key.funKey(UUID(10, 10)) },
//        Supplier { mapOf(Pair(MembershipIndexService.MEMBEROF, UUID(10, 10).toString())) }
//)
class UserIndexTests {

    @MockBean
    private lateinit var byHandleRepo: ChatUserHandleRepository<UUID>

    @MockBean
    lateinit var userIndex: UserIndexService<UUID, Map<String, String>>

    @BeforeEach
    fun setUp() {
        userIndex = UserIndex(byHandleRepo)
    }

    @Test
    fun `add user success`() {
        BDDMockito.given(byHandleRepo.findByKeyHandle(anyObject()))
            .willReturn(Mono.empty())
        BDDMockito.given(byHandleRepo.add(anyObject()))
            .willReturn(Mono.empty())

        val user = User.create(Key.funKey(UUIDs.random()), "test-name", "test-handle", "localhost/test.jpg")

        StepVerifier.create(userIndex.add(user))
                .expectSubscription()
                .verifyComplete()
    }


    fun getIndex(): IndexService<UUID, User<UUID>, Map<String, String>> = userIndex
}