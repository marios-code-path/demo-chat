package com.demo.chat.test.controller.webflux.config

import com.demo.chat.config.IndexServiceBeans
import com.demo.chat.config.KeyServiceBeans
import com.demo.chat.config.PersistenceServiceBeans
import com.demo.chat.config.PubSubServiceBeans
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.service.core.IKeyService
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

interface TestKeyService : IKeyService<Long>

@TestConfiguration
class LongKeyTestConfiguration {
    @Bean
    fun keyServiceBean(tk: TestKeyService): KeyServiceBeans<Long> =
        object : KeyServiceBeans<Long> {
            override fun keyService(): IKeyService<Long> = tk
        }
}

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

@TestConfiguration
class LongPubSubBeans {

    @Bean
    fun pubsubBeans(): PubSubServiceBeans<Long, String> = TestPubSubBeans()
}