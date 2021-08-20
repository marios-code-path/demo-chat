package com.demo.chat.streams.app

import com.demo.chat.codec.JsonNodeAnyDecoder
import com.demo.chat.domain.*
import com.demo.chat.domain.serializers.JacksonModules
import com.demo.chat.service.IKeyService
import com.demo.chat.streams.functions.*
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
    class AppUserFunctions(persist: UserCreateStore) : UserFunctions<Long, IndexSearchRequest>(persist) {
        @Bean
        fun receiveUserRequest() = this.userCreateFunction()
    }

    @Configuration
    class AppMessageTopicFunctions(persist: TopicCreateStore) : MessageTopicFunctions<Long, IndexSearchRequest>(persist) {
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