package com.demo.chat.config.controller.core

import com.demo.chat.domain.knownkey.ChatDomain

import com.demo.chat.controller.resolve.KeyDomain

import com.demo.chat.service.core.KeyVerifier

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
    @ConditionalOnProperty(prefix = "app.service.core", name = ["index"], havingValue = "lucene")
    @KeyDomain(ChatDomain.KEY_VALUE_PAIR)
    class KeyValueIndexController<T, V>(s: IndexServiceBeans<T, V, IndexSearchRequest>, v: KeyVerifier<T>):
        IndexSearchRequestIndexServiceController<T, KeyValuePair<T, Any>>(s.KVPairIndex(), v)

    @Controller
    @MessageMapping("index.user")
    @ConditionalOnProperty(prefix = "app.controller", name = ["index"])
    @ConditionalOnProperty(prefix = "app.service.core", name = ["index"], havingValue = "lucene")
    @KeyDomain(ChatDomain.USER)
    class UserIndexController<T, V>(s: IndexServiceBeans<T, V, IndexSearchRequest>, v: KeyVerifier<T>) :
        IndexSearchRequestIndexServiceController<T, User<T>>(s.userIndex(), v)

    @Controller
    @MessageMapping("index.message")
    @ConditionalOnProperty(prefix = "app.controller", name = ["index"])
    @ConditionalOnProperty(prefix = "app.service.core", name = ["index"], havingValue = "lucene")
    @KeyDomain(ChatDomain.MESSAGE)
    class MessageIndexController<T, V>(s: IndexServiceBeans<T, V, IndexSearchRequest>, v: KeyVerifier<T>) :
        IndexSearchRequestIndexServiceController<T, Message<T, V>>(s.messageIndex(), v)

    @Controller
    @MessageMapping("index.topic")
    @ConditionalOnProperty(prefix = "app.controller", name = ["index"])
    @ConditionalOnProperty(prefix = "app.service.core", name = ["index"], havingValue = "lucene")
    @KeyDomain(ChatDomain.MESSAGE_TOPIC)
    class TopicIndexController<T, V>(s: IndexServiceBeans<T, V, IndexSearchRequest>, v: KeyVerifier<T>) :
        IndexSearchRequestIndexServiceController<T, MessageTopic<T>>(s.topicIndex(), v)

    @Controller
    @MessageMapping("index.authmetadata")
    @ConditionalOnProperty(prefix = "app.controller", name = ["index"])
    @ConditionalOnProperty(prefix = "app.service.core", name = ["index"], havingValue = "lucene")
    @KeyDomain(ChatDomain.AUTH_METADATA)
    class AuthMetaIndexController<T, V>(s: IndexServiceBeans<T, V, IndexSearchRequest>, v: KeyVerifier<T>) :
        IndexSearchRequestIndexServiceController<T, AuthMetadata<T>>(s.authMetadataIndex(), v)

    @Controller
    @MessageMapping("index.kv")
    @ConditionalOnProperty(prefix = "app.controller", name = ["index"])
    @ConditionalOnProperty(prefix = "app.service.core", name = ["index"], havingValue = "cassandra")
    @KeyDomain(ChatDomain.KEY_VALUE_PAIR)
    class CassandraKeyValueIndexController<T, V>(s: IndexServiceBeans<T, V, Map<String, String>>, v: KeyVerifier<T>):
        MapIndexServiceController<T, KeyValuePair<T, Any>>(s.KVPairIndex(), v)

    @Controller
    @MessageMapping("index.user")
    @ConditionalOnProperty(prefix = "app.controller", name = ["index"])
    @ConditionalOnProperty(prefix = "app.service.core", name = ["index"], havingValue = "cassandra")
    @KeyDomain(ChatDomain.USER)
    class CassandraUserIndexController<T, V>(s: IndexServiceBeans<T, V, Map<String, String>>, v: KeyVerifier<T>) :
        MapIndexServiceController<T, User<T>>(s.userIndex(), v)

    @Controller
    @MessageMapping("index.message")
    @ConditionalOnProperty(prefix = "app.controller", name = ["index"])
    @ConditionalOnProperty(prefix = "app.service.core", name = ["index"], havingValue = "cassandra")
    @KeyDomain(ChatDomain.MESSAGE)
    class CassandraMessageIndexController<T, V>(s: IndexServiceBeans<T, V, Map<String, String>>, v: KeyVerifier<T>) :
        MapIndexServiceController<T, Message<T, V>>(s.messageIndex(), v)

    @Controller
    @MessageMapping("index.topic")
    @ConditionalOnProperty(prefix = "app.controller", name = ["index"])
    @ConditionalOnProperty(prefix = "app.service.core", name = ["index"], havingValue = "cassandra")
    @KeyDomain(ChatDomain.MESSAGE_TOPIC)
    class CassandraTopicIndexController<T, V>(s: IndexServiceBeans<T, V, Map<String, String>>, v: KeyVerifier<T>) :
        MapIndexServiceController<T, MessageTopic<T>>(s.topicIndex(), v)

    @Controller
    @MessageMapping("index.authmetadata")
    @ConditionalOnProperty(prefix = "app.controller", name = ["index"])
    @ConditionalOnProperty(prefix = "app.service.core", name = ["index"], havingValue = "cassandra")
    @KeyDomain(ChatDomain.AUTH_METADATA)
    class CassandraAuthMetaIndexController<T, V>(s: IndexServiceBeans<T, V, Map<String, String>>, v: KeyVerifier<T>) :
        MapIndexServiceController<T, AuthMetadata<T>>(s.authMetadataIndex(), v)
}
