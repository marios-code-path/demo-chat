package com.demo.chat.test

import com.demo.chat.ChatServiceRsocketApplication
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Import

@SpringBootTest
@Import(ChatServiceRsocketApplication::class)
class ApplicationContextTests {

    @Test
    fun `context should load`(context: ApplicationContext) {
        println(context.beanDefinitionCount)
    }
}