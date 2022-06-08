package com.demo.chat.domain.lucene

import com.demo.chat.domain.*
import com.demo.chat.service.MembershipIndexService
import java.util.function.Function

fun interface IndexEntryEncoder<E> : Function<E, List<Pair<String, String>>> {
    companion object Factory {
        fun <T> ofUser(): IndexEntryEncoder<User<T>> =
            IndexEntryEncoder { t ->
                listOf(
                    Pair("key", t.key.id.toString()),
                    Pair("handle", t.handle),
                    Pair("name", t.name)
                )
            }

        fun <T> ofMessage(): IndexEntryEncoder<Message<T, String>> =
            IndexEntryEncoder { t ->
                listOf(
                    Pair("key", t.key.id.toString()),
                    Pair("text", t.data)
                )
            }

        fun <T> ofTopic(): IndexEntryEncoder<MessageTopic<T>> =
            IndexEntryEncoder { t ->
                listOf(
                    Pair("key", t.key.id.toString()),
                    Pair("name", t.data)
                )
            }

        fun <T> ofTopicMembership(): IndexEntryEncoder<TopicMembership<T>> =
            IndexEntryEncoder { t ->
                listOf(
                    Pair("key", Key.funKey(t.key).toString()),
                    Pair(MembershipIndexService.MEMBER, t.member.toString()),
                    Pair(MembershipIndexService.MEMBEROF, t.memberOf.toString())
                )
            }
    }
}