package com.demo.chat

import com.demo.chat.repository.cassandra.ChatUserRepository
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.ApplicationContext
import org.springframework.test.context.junit.jupiter.SpringExtension


@ExtendWith(SpringExtension::class)
class ChatRSocketUserTests {


    @Test
    fun `should start context`() {
//        Assertions
//                .assertThat(applicationContext)
//                .`as`("Context is available")
//                .isNotNull
    }
}