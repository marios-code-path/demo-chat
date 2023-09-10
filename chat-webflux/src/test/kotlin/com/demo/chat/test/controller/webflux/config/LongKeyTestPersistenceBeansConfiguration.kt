package com.demo.chat.test.controller.webflux.config

import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.test.controller.webflux.config.TestPersistenceBeans
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

@TestConfiguration
class LongKeyTestPersistenceBeansConfiguration {

    @Bean
    fun persistenceBeans(): PersistenceServiceBeans<Long, String> = TestPersistenceBeans()
}