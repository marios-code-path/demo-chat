package com.demo.chat.config.controller.core

import com.demo.chat.config.IndexServiceBeans
import com.demo.chat.controller.core.IndexSearchRequestIndexServiceController
import com.demo.chat.controller.core.MapIndexServiceController
import com.demo.chat.domain.*
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.stereotype.Controller

@Configuration
class IndexControllersConfiguration {

    @Controller
    @MessageMapping("index.kv")
    @ConditionalOnProperty(prefix = "app.controller", name = ["index"])
    @ConditionalOnProperty(prefix = "app.service.core", name = ["index"], havingValue = "lucene", matchIfMissing = true)
    class KeyValueIndexController<T, V>(s: IndexServiceBeans<T, V, IndexSearchRequest>):
        IndexSearchRequestIndexServiceController<T, KeyValuePair<T, Any>>(s.KVPairIndex())

    @Controller
    @MessageMapping("index.user")
    @ConditionalOnProperty(prefix = "app.controller", name = ["index"])
    @ConditionalOnProperty(prefix = "app.service.core", name = ["index"], havingValue = "lucene", matchIfMissing = true)
    class UserIndexController<T, V>(s: IndexServiceBeans<T, V, IndexSearchRequest>) :
        IndexSearchRequestIndexServiceController<T, User<T>>(s.userIndex())

    @Controller
    @MessageMapping("index.message")
    @ConditionalOnProperty(prefix = "app.controller", name = ["index"])
    @ConditionalOnProperty(prefix = "app.service.core", name = ["index"], havingValue = "lucene", matchIfMissing = true)
    class MessageIndexController<T, V>(s: IndexServiceBeans<T, V, IndexSearchRequest>) :
        IndexSearchRequestIndexServiceController<T, Message<T, V>>(s.messageIndex())

    @Controller
    @MessageMapping("index.topic")
    @ConditionalOnProperty(prefix = "app.controller", name = ["index"])
    @ConditionalOnProperty(prefix = "app.service.core", name = ["index"], havingValue = "lucene", matchIfMissing = true)
    class TopicIndexController<T, V>(s: IndexServiceBeans<T, V, IndexSearchRequest>) :
        IndexSearchRequestIndexServiceController<T, MessageTopic<T>>(s.topicIndex())

    @Controller
    @MessageMapping("index.authmetadata")
    @ConditionalOnProperty(prefix = "app.controller", name = ["index"])
    @ConditionalOnProperty(prefix = "app.service.core", name = ["index"], havingValue = "lucene", matchIfMissing = true)
    class AuthMetaIndexController<T, V>(s: IndexServiceBeans<T, V, IndexSearchRequest>) :
        IndexSearchRequestIndexServiceController<T, AuthMetadata<T>>(s.authMetadataIndex())

    @Controller
    @MessageMapping("index.kv")
    @ConditionalOnProperty(prefix = "app.controller", name = ["index"])
    @ConditionalOnProperty(prefix = "app.service.core", name = ["index"], havingValue = "cassandra")
    class CassandraKeyValueIndexController<T, V>(s: IndexServiceBeans<T, V, Map<String, String>>):
        MapIndexServiceController<T, KeyValuePair<T, Any>>(s.KVPairIndex())

    @Controller
    @MessageMapping("index.user")
    @ConditionalOnProperty(prefix = "app.controller", name = ["index"])
    @ConditionalOnProperty(prefix = "app.service.core", name = ["index"], havingValue = "cassandra")
    class CassandraUserIndexController<T, V>(s: IndexServiceBeans<T, V, Map<String, String>>) :
        MapIndexServiceController<T, User<T>>(s.userIndex())

    @Controller
    @MessageMapping("index.message")
    @ConditionalOnProperty(prefix = "app.controller", name = ["index"])
    @ConditionalOnProperty(prefix = "app.service.core", name = ["index"], havingValue = "cassandra")
    class CassandraMessageIndexController<T, V>(s: IndexServiceBeans<T, V, Map<String, String>>) :
        MapIndexServiceController<T, Message<T, V>>(s.messageIndex())

    @Controller
    @MessageMapping("index.topic")
    @ConditionalOnProperty(prefix = "app.controller", name = ["index"])
    @ConditionalOnProperty(prefix = "app.service.core", name = ["index"], havingValue = "cassandra")
    class CassandraTopicIndexController<T, V>(s: IndexServiceBeans<T, V, Map<String, String>>) :
        MapIndexServiceController<T, MessageTopic<T>>(s.topicIndex())

    @Controller
    @MessageMapping("index.authmetadata")
    @ConditionalOnProperty(prefix = "app.controller", name = ["index"])
    @ConditionalOnProperty(prefix = "app.service.core", name = ["index"], havingValue = "cassandra")
    class CassandraAuthMetaIndexController<T, V>(s: IndexServiceBeans<T, V, Map<String, String>>) :
        MapIndexServiceController<T, AuthMetadata<T>>(s.authMetadataIndex())
}
