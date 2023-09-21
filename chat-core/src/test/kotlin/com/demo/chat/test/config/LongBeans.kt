package com.demo.chat.test.config

import com.demo.chat.config.*
import com.demo.chat.domain.IndexSearchRequest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

@TestConfiguration
open class LongPersistenceBeans {

    @Bean
    open fun persistenceBeans(): PersistenceServiceBeans<Long, String> = TestPersistenceBeans()
}

@TestConfiguration
open class LongIndexBeans {

    @Bean
    open fun indexBeans(): IndexServiceBeans<Long, String, IndexSearchRequest> = TestIndexBeans()
}

@TestConfiguration
open class LongPubSubBeans {

    @Bean
    open fun pubsubBeans(): PubSubServiceBeans<Long, String> = TestPubSubBeans()
}

@TestConfiguration
open class LongSecretsStoreBeans {

    @Bean
    open fun secretsStoreBeans(): SecretsStoreBeans<Long> = TestSecretsStoreBeans()
}

@TestConfiguration
open class LongKeyServiceBeans {

    @Bean
    open fun keyServiceBeans(): KeyServiceBeans<Long> = TestKeyServiceBeans()
}

@TestConfiguration
open class LongCompositeServiceBeans {

    @Bean
    open fun compositeServiceBeans(): CompositeServiceBeans<Long, String> = TestCompositeServiceBeans()
}