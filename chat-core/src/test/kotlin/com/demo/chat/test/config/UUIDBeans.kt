package com.demo.chat.test.config

import com.demo.chat.config.*
import com.demo.chat.domain.IndexSearchRequest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import java.util.*


@TestConfiguration
open class UUIDKeyServiceBeans {

    @Bean
    open fun keyServiceBeans(): KeyServiceBeans<UUID> = TestKeyServiceBeans()
}

@TestConfiguration
open class UUIDPersistenceBeans {

    @Bean
    open fun persistenceBeans(): PersistenceServiceBeans<UUID, String> = TestPersistenceBeans()
}

@TestConfiguration
open class UUIDIndexBeans {

    @Bean
    open fun indexBeans(): IndexServiceBeans<UUID, String, IndexSearchRequest> = TestIndexBeans()
}

@TestConfiguration
open class UUIDPubSubBeans {

    @Bean
    open fun pubsubBeans(): PubSubServiceBeans<UUID, String> = TestPubSubBeans()
}

@TestConfiguration
open class UUIDSecretsStoreBeans {

    @Bean
    open fun secretsStoreBeans(): SecretsStoreBeans<UUID> = TestSecretsStoreBeans()
}
