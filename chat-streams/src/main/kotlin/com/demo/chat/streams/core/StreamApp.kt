package com.demo.chat.streams.core

import com.demo.chat.codec.JsonNodeAnyDecoder
import com.demo.chat.config.index.memory.InMemoryIndexBeans
import com.demo.chat.config.memory.InMemoryPersistenceBeans
import com.demo.chat.deploy.config.core.KeyServiceConfiguration
import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.domain.serializers.JacksonModules
import com.demo.chat.service.*
import com.demo.chat.service.conflate.KeyEnricherPersistenceStore
import com.demo.chat.service.impl.lucene.index.IndexEntryEncoder
import com.demo.chat.service.impl.lucene.index.StringToKeyEncoder
import com.demo.chat.service.impl.memory.persistence.InMemoryPersistence
import com.demo.chat.service.impl.memory.persistence.KeyServiceInMemory
import com.demo.chat.service.impl.memory.persistence.UserPersistenceInMemory
import com.demo.chat.streams.core.persistence.UserRequestStream
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor
import org.springframework.beans.factory.support.GenericBeanDefinition
import org.springframework.beans.factory.support.RootBeanDefinition
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.context.event.ApplicationStartingEvent
import org.springframework.boot.runApplication
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.EventListener
import org.springframework.context.support.GenericApplicationContext
import reactor.core.publisher.Hooks
import java.util.function.Supplier
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

    class UserCreatePersistence(store: PersistenceStore<Long, User<Long>>) :
        KeyEnricherPersistenceStore<Long, UserCreateRequest, User<Long>>(
            store,
            { req, key -> User.create(key, req.name, req.handle, req.imgUri) })

    @Configuration
    class PersistenceBeans(keyService: IKeyService<Long>) : InMemoryPersistenceBeans<Long, String>(keyService) {
        @Bean
        fun userPersistence() = UserCreatePersistence(user())
    }

    @Configuration
    class ChatUserRequestStream(
        persist: EnricherPersistenceStore<Long, UserCreateRequest, User<Long>>,
        index: IndexService<Long, User<Long>, IndexSearchRequest>
    ) : UserRequestStream<Long, IndexSearchRequest>(persist, index)

//    @Configuration
//    class ChatTopicRequestStream(
//        persist: PersistenceStore<Long, MessageTopic<Long>>,
//        index: IndexService<Long, MessageTopic<Long>, IndexSearchRequest>
//    ) : TopicRequestStream<Long, IndexSearchRequest>(persist, index)

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
        fun user() = userIndex()
    }
}