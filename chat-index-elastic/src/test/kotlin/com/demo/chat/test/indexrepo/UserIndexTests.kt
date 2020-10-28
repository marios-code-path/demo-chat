package com.demo.chat.test.indexrepo

import com.demo.chat.domain.elastic.ChatUser
import com.demo.chat.domain.elastic.ChatUserKey
import com.demo.chat.repository.elastic.ReactiveUserIndexRepository
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.test.StepVerifier
import java.time.Instant

@ExtendWith(SpringExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = [ElasticContainerConfiguration::class])
class UserIndexTests {
    @Autowired
    private lateinit var repo: ReactiveUserIndexRepository<String>

    @Test
    fun `should store to index and success`() {
        val user = ChatUser(ChatUserKey("1234"),
                "testuser",
                "testhandle",
                "http://",
                Instant.now())

        StepVerifier
                .create(repo.save(user))
                .assertNext {
                    Assertions
                            .assertThat(it)
                            .isNotNull
                }
                .verifyComplete()
    }
}