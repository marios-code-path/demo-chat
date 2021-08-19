package com.demo.chat.streams.app

import com.demo.chat.codec.JsonNodeAnyDecoder
import com.demo.chat.config.index.memory.InMemoryIndexBeans
import com.demo.chat.config.memory.InMemoryPersistenceBeans
import com.demo.chat.deploy.config.core.KeyServiceConfiguration
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.MessageTopic
import com.demo.chat.domain.User
import com.demo.chat.domain.serializers.JacksonModules
import com.demo.chat.service.EnricherPersistenceStore
import com.demo.chat.service.IKeyService
import com.demo.chat.service.IndexService
import com.demo.chat.service.MembershipIndexService
import com.demo.chat.service.impl.lucene.index.IndexEntryEncoder
import com.demo.chat.service.impl.lucene.index.StringToKeyEncoder
import com.demo.chat.service.impl.memory.persistence.KeyServiceInMemory
import com.demo.chat.streams.functions.MessageTopicRequest
import com.demo.chat.streams.functions.TopicFunctions
import com.demo.chat.streams.functions.UserCreateRequest
import com.demo.chat.streams.functions.UserFunctions
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import reactor.core.publisher.Hooks
import kotlin.random.Random

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
    class MemoryKeyServiceFactory : KeyServiceConfiguration<Long> {
        @Bean
        override fun keyService() = KeyServiceInMemory { kotlin.math.abs(Random.nextLong()) }
    }

    @Configuration
    class ChatUserRequestFunctions(
        persist: EnricherPersistenceStore<Long, UserCreateRequest, User<Long>>,
        index: IndexService<Long, User<Long>, IndexSearchRequest>
    ) : UserFunctions<Long, IndexSearchRequest>(persist, index) {
        @Bean
        fun receiveUserRequest() = this.userCreateFunction()
    }

    //    @Configuration
    class ChatTopicRequestStream(
        persist: EnricherPersistenceStore<Long, MessageTopicRequest, MessageTopic<Long>>,
        index: IndexService<Long, MessageTopic<Long>, IndexSearchRequest>
    ) : TopicFunctions<Long, IndexSearchRequest>(persist, index)

    @Configuration
    class PersistenceBeans(keyService: IKeyService<Long>) : InMemoryPersistenceBeans<Long, String>(keyService) {
        @Bean
        fun userPersistence() = UserCreatePersistence(user())

        @Bean
        fun topicPersistence() = TopicCreatePersistence(topic())

        @Bean
        fun messagePersistence() = MessageCreatePersistence(message())

        @Bean
        fun membershipPersistence() = MembershipCreatePersistence(membership())
    }


    @Configuration
    class IndexBeans : InMemoryIndexBeans<Long, String>(
        StringToKeyEncoder { i -> Key.funKey(i.toLong()) },
        IndexEntryEncoder { t ->
            listOf(
                Pair("key", t.key.id.toString()),
                Pair("handle", t.handle),
                Pair("name", t.name)
            )
        },
        IndexEntryEncoder { t ->
            listOf(
                Pair("key", t.key.id.toString()),
                Pair("text", t.data)
            )
        },
        IndexEntryEncoder { t ->
            listOf(
                Pair("key", t.key.id.toString()),
                Pair("name", t.data)
            )
        },
        IndexEntryEncoder { t ->
            listOf(
                Pair("key", Key.funKey(t.key).toString()),
                Pair(MembershipIndexService.MEMBER, t.member.toString()),
                Pair(MembershipIndexService.MEMBEROF, t.memberOf.toString())
            )
        }) {
        @Bean
        fun idxUser() = userIndex()

        @Bean
        fun idxTopic() = topicIndex()

        @Bean
        fun idxMembership() = membershipIndex()

        @Bean
        fun idxMessage() = messageIndex()
    }
}