package com.demo.chat.service.impl

import com.demo.chat.domain.Message
import com.demo.chat.config.DefaultChatJacksonModules
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.util.function.Function


class MessageEnrichingFunction : Function<String, String> {

//    private val keyServices: LongKeyServiceBeans = LongKeyServiceBeans()
//
//    private val userPersistence: UserPersistence<Long> =
//        UserPersistenceInMemory(keyServices.keyService()) { t -> t.key }
//            .apply {
//                this.add(User.create(Key.funKey(1L), "TESTName", "TestHandle", "https://test-url"))
//            }
//  msg[userId, to, text] -> streams { UserLookUp -> MessageComp } -> messages
    override fun apply(p1: String): String {
        val mapper: ObjectMapper = ObjectMapper().apply {
            registerModules(DefaultChatJacksonModules().messageModule())
        }

        val message: Message<Long, String> = mapper.readValue(p1)

        return "user: ${message.data}"
//        userPersistence.byIds(listOf(Key.funKey(message.key.from)))
//            .map { user -> "${user.handle}: ${message.data}" }
//            .last()
//            .defaultIfEmpty("noSuchUser: ${message.data}")
//            .block()!!
    }
}