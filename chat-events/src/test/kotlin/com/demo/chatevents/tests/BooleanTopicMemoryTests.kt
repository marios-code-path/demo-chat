package com.demo.chatevents.tests

import com.demo.chat.domain.EventKey
import com.demo.chat.service.BooleanTopicService
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.context.junit.jupiter.SpringExtension
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object TestTopicService : BooleanTopicService<EventKey, String> {
    val booleanSet: MutableList<Pair<EventKey, String>> = mutableListOf()

    override fun add(topic: EventKey, message: String): Mono<EventKey> =
            Mono.just(EventKey.create(UUID.randomUUID()).apply {
                booleanSet
                        .add(Pair(this, message))
            })


    override fun compute(topic: EventKey): Mono<Set<String>> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun reset(topic: EventKey, startKey: EventKey): Mono<EventKey> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun subscribe(member: EventKey, topic: EventKey): Mono<Void> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun unSubscribe(member: EventKey, topic: EventKey): Mono<Void> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun unSubscribeAll(member: EventKey): Mono<Void> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun unSubscribeAllIn(topic: EventKey): Mono<Void> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun sendMessage(message: String): Mono<Void> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun receiveOn(topic: EventKey): Flux<out String> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun receiveSourcedEvents(topic: EventKey): Flux<out String> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun exists(topic: EventKey): Mono<Boolean> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}

//@ExtendWith(SpringExtension::class)
//@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BooleanTopicMemoryTests {//}: BooleanTopicTestBase() {

    @BeforeAll
    fun setUp() {
       // svc = TestTopicService
    }
}