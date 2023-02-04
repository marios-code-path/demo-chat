package com.demo.chat.deploy.memory.config

import com.demo.chat.index.lucene.config.LuceneIndexBeans
import com.demo.chat.domain.TypeUtil
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration

@Configuration
class CoreResourceConfiguration {

    @Configuration
    @ConditionalOnProperty(prefix = "app.service.core", name = ["index"])
    class IndexBeans<T>(typeUtil: TypeUtil<T>) : LuceneIndexBeans<T>(
        typeUtil
    )
}