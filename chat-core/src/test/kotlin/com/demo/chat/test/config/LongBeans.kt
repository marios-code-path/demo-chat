package com.demo.chat.test.config

import com.demo.chat.config.*
import com.demo.chat.domain.IndexSearchRequest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

@TestConfiguration
open class TestLongPersistenceBeans {

    @Bean
    open fun persistenceBeans(): PersistenceServiceBeans<Long, String> = TestPersistenceBeans()
}

@TestConfiguration
open class TestLongIndexBeans {

    @Bean
    open fun indexBeans(): IndexServiceBeans<Long, String, IndexSearchRequest> = TestIndexBeans()
}

@TestConfiguration
open class TestLongPubSubBeans {

    @Bean
    open fun pubsubBeans(): PubSubServiceBeans<Long, String> = TestPubSubBeans()
}

@TestConfiguration
open class TestLongSecretsStoreBeans {

    @Bean
    open fun secretsStoreBeans(): SecretsStoreBeans<Long> = TestSecretsStoreBeans()
}

@TestConfiguration
open class TestLongKeyServiceBeans {

    @Bean
    open fun keyServiceBeans(): KeyServiceBeans<Long> = TestKeyServiceBeans()
}

@TestConfiguration
open class TestLongCompositeServiceBeans {

    @Bean
    open fun compositeServiceBeans(): CompositeServiceBeans<Long, String> = TestCompositeServiceBeans()
}