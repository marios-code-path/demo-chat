package com.demo.chatevents.tests

import com.demo.chat.domain.Key
import com.demo.chat.domain.UUIDKey
import com.demo.chat.service.BooleanTopicService
import org.junit.jupiter.api.BeforeAll
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

object TestTopicService : BooleanTopicService<UUIDKey, String> {
    val BOOLEAN_SET: MutableList<Pair<UUIDKey, String>> = mutableListOf()

    override fun add(topic: UUIDKey, message: String): Mono<UUIDKey> =
            Mono.just(Key.eventKey(UUID.randomUUID()).apply {
                BOOLEAN_SET
                        .add(Pair(this, message))
            })


    override fun compute(topic: UUIDKey): Mono<Set<String>> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun reset(topic: UUIDKey, startKey: UUIDKey): Mono<UUIDKey> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun subscribe(member: UUIDKey, topic: UUIDKey): Mono<Void> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun unSubscribe(member: UUIDKey, topic: UUIDKey): Mono<Void> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun unSubscribeAll(member: UUIDKey): Mono<Void> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun unSubscribeAllIn(topic: UUIDKey): Mono<Void> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun sendMessage(message: String): Mono<Void> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun receiveOn(topic: UUIDKey): Flux<out String> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun receiveSourcedEvents(topic: UUIDKey): Flux<out String> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun exists(topic: UUIDKey): Mono<Boolean> {
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