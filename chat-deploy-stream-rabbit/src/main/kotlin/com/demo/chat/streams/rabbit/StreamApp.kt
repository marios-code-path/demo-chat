package com.demo.chat.streams.rabbit

import com.demo.chat.config.memory.LongKeyServiceBeans
import com.demo.chat.convert.JsonNodeToAnyConverter
import com.demo.chat.domain.*
import com.demo.chat.domain.serializers.JacksonModules
import com.demo.chat.service.IKeyService
import com.demo.chat.streams.app.config.*
import com.demo.chat.streams.functions.*
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@SpringBootApplication
class StreamApp {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            runApplication<StreamApp>(*args)
        }
    }

    @Configuration
    class AppJacksonModules : JacksonModules(JsonNodeToAnyConverter, JsonNodeToAnyConverter)

    @Configuration
    class AppLongKeyBeans : LongKeyServiceBeans()

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