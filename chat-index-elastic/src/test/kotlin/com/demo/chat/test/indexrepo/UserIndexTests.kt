package com.demo.chat.test.indexrepo

import com.demo.chat.config.index.elastic.ElasticConfiguration
import com.demo.chat.domain.elastic.ChatUser
import com.demo.chat.domain.elastic.ChatUserKey
import com.demo.chat.repository.elastic.ReactiveUserIndexRepository
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.elasticsearch.repository.config.EnableReactiveElasticsearchRepositories
import reactor.test.StepVerifier
import java.time.Instant


@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = [ElasticConfiguration::class, ElasticContainerBase.ConfConfig::class]
)
@EnableReactiveElasticsearchRepositories(basePackages = ["com.demo.chat"])
class UserIndexTests : ElasticContainerBase() {

    @Autowired
    private lateinit var repo: ReactiveUserIndexRepository<String>

    @Test
    fun `should store to index and success`() {
        val user = ChatUser(
            ChatUserKey("1234"),
            "testuser",
            "testhandle",
            "http://",
            Instant.now()
        )

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