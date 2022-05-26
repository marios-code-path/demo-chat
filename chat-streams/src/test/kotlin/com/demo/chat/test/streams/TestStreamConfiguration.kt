package com.demo.chat.test.streams

import com.demo.chat.convert.JsonNodeToAnyConverter
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.serializers.JacksonModules
import com.demo.chat.service.IKeyService
import com.demo.chat.streams.app.config.*
import com.demo.chat.streams.functions.MembershipFunctions
import com.demo.chat.streams.functions.MessageFunctions
import com.demo.chat.streams.functions.MessageTopicFunctions
import com.demo.chat.streams.functions.UserFunctions
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@SpringBootApplication
class TestStreamConfiguration {

    @Bean
    fun mapper(modules: JacksonModules): ObjectMapper = ObjectMapper().registerModule(KotlinModule()).apply {
        propertyNamingStrategy = PropertyNamingStrategy.LOWER_CAMEL_CASE
        setSerializationInclusion(JsonInclude.Include.NON_NULL)
        findAndRegisterModules()
    }.apply {
        registerModules(modules.membershipModule())
        registerModules(modules.messageModule())
        registerModules(modules.keyModule())
        registerModules(modules.userModule())
        registerModules(modules.topicModule())
    }

    @Configuration
    class AppJacksonModules : JacksonModules(JsonNodeToAnyConverter, JsonNodeToAnyConverter)

    @Configuration
    class AppLongKeyConfiguration : MemoryLongKeyServiceConfiguration()

    @Configuration
    class AppPersistenceBeans(keySvc: IKeyService<Long>) : PersistenceBeans(keySvc)

    @Configuration
    class AppIndexBeans : IndexBeans()

    @Configuration
    class AppUserFunctions(persist: UserCreateStore) : UserFunctions<Long, IndexSearchRequest>(persist) {
        @Bean
        fun receiveUserRequest() = this.userCreateFunction()
    }

    @Configuration
    class AppMessageTopicFunctions(persist: TopicCreateStore) :
        MessageTopicFunctions<Long, IndexSearchRequest>(persist) {
        @Bean
        fun receiveTopicRequest() = this.topicCreateFunction()
    }

    @Configuration
    class AppMembershipFunctions(persist: MembershipCreateStore) :
        MembershipFunctions<Long, IndexSearchRequest>(persist) {
        @Bean
        fun receiveMembershipRequest() = this.membershipCreateFunction()
    }

    @Configuration
    class AppMessageFunctions(persist: MessageCreateStore) :
        MessageFunctions<Long, String, IndexSearchRequest>(persist) {
        @Bean
        fun receiveMessageRequest() = this.messageCreateFunction()
    }
}