package com.demo.chat.test.controller.webflux.config

import com.demo.chat.config.IndexServiceBeans
import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.domain.IndexSearchRequest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

@TestConfiguration
class LongPersistenceBeans {

    @Bean
    fun persistenceBeans(): PersistenceServiceBeans<Long, String> = TestPersistenceBeans()
}

@TestConfiguration
class LongIndexBeans {

    @Bean
    fun indexBeans(): IndexServiceBeans<Long, String, IndexSearchRequest> = TestIndexBeans()
}