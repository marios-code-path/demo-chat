package com.demo.chat.streams.core

import com.demo.chat.domain.IndexSearchRequest
import com.demo.chat.domain.Key
import com.demo.chat.domain.User
import com.demo.chat.service.IKeyService
import com.demo.chat.service.IndexService
import com.demo.chat.service.PersistenceStore
import com.demo.chat.service.impl.lucene.index.LuceneIndex
import com.demo.chat.service.impl.memory.persistence.KeyServiceInMemory
import com.demo.chat.service.impl.memory.persistence.UserPersistenceInMemory
import com.demo.chat.streams.core.persistence.UserRequestStream
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cloud.stream.annotation.EnableBinding
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import reactor.core.publisher.Hooks
import java.util.function.Supplier
import kotlin.random.Random

@SpringBootApplication
@EnableBinding(CoreStreams::class)
class StreamApp {
    companion object {

        @JvmStatic
        fun main(args: Array<String>) {
            Hooks.onOperatorDebug()
            runApplication<StreamApp>(*args)
        }
    }

    @Bean
    fun chatUserRequestStream(
        persist: PersistenceStore<Long, User<Long>>,
        index: IndexService<Long, User<Long>, IndexSearchRequest>
    ) = UserRequestStream(persist, index)


    @Configuration
    class AppConfig {
        @Bean
        fun keyService(): IKeyService<Long> = KeyServiceInMemory(Supplier { kotlin.math.abs(Random.nextLong()) })

        @Bean
        fun userPersistence(keySvc: IKeyService<Long>): PersistenceStore<Long, User<Long>> =
            UserPersistenceInMemory(keySvc) { u -> u.key }

        @Bean
        fun userIndex(): IndexService<Long, User<Long>, IndexSearchRequest> =
            LuceneIndex({ t ->
                listOf(
                    Pair("handle", t.handle),
                    Pair("name", t.name)
                )
            },
                { q -> Key.funKey(q.toLong()) },
                { t -> t.key })
    }
}