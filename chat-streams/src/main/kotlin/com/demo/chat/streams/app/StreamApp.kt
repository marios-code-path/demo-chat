package com.demo.chat.streams.app

import com.demo.chat.codec.JsonNodeAnyDecoder
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.User
import com.demo.chat.domain.serializers.JacksonModules
import com.demo.chat.service.EnricherPersistenceStore
import com.demo.chat.service.IKeyService
import com.demo.chat.service.IndexService
import com.demo.chat.streams.functions.MessageTopicRequest
import com.demo.chat.streams.functions.TopicFunctions
import com.demo.chat.streams.functions.UserCreateRequest
import com.demo.chat.streams.functions.UserFunctions
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import reactor.core.publisher.Hooks

@SpringBootApplication(excludeName = ["com.demo.chat.deploy"])
class StreamApp {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            Hooks.onOperatorDebug()
            runApplication<StreamApp>(*args)
        }
    }

    @Configuration
    class AppJacksonModules : JacksonModules(JsonNodeAnyDecoder, JsonNodeAnyDecoder)

    @Configuration
    class AppKeyConfiguration : MemoryKeyServiceConfiguration()

    @Configuration
    class AppPersistenceBeans(keySvc: IKeyService<Long>) : PersistenceBeans(keySvc)

    @Configuration
    class AppIndexBeans : IndexBeans()

    @Configuration
    class AppUserFunctions(
        persist: EnricherPersistenceStore<Long, UserCreateRequest, User<Long>>,
        index: IndexService<Long, User<Long>, IndexSearchRequest>
    ) : UserFunctions<Long, IndexSearchRequest>(persist, index) {
        @Bean
        fun receiveUserRequest() = this.userCreateFunction()
    }

    @Configuration
    class AppTopicFunctions(
        persist: EnricherPersistenceStore<Long, MessageTopicRequest, MessageTopic<Long>>,
        index: IndexService<Long, MessageTopic<Long>, IndexSearchRequest>
    ) : TopicFunctions<Long, IndexSearchRequest>(persist, index) {
        @Bean
        fun receiveTopicRequest() = this.topicCreateFunction()
    }
}