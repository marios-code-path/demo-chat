package com.demo.chat.index.lucene.domain

import com.demo.chat.domain.*
import com.demo.chat.service.core.MembershipIndexService
import com.demo.chat.service.core.MessageIndexService
import com.demo.chat.service.security.AuthMetaIndex
import java.util.function.Function

fun interface IndexEntryEncoder<E> : Function<E, List<Pair<String, String>>> {
    companion object Factory {
        fun <T> ofAuthMeta(typeUtil: TypeUtil<T>): IndexEntryEncoder<AuthMetadata<T>> =
            IndexEntryEncoder { t ->
                listOf(
                    Pair(AuthMetaIndex.PRINCIPAL, typeUtil.toString(t.principal.id)),
                    Pair(AuthMetaIndex.TARGET, typeUtil.toString(t.target.id)),
                    Pair(AuthMetaIndex.ID, typeUtil.toString(t.key.id))
                )
            }

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
                    Pair(MessageIndexService.ID, t.key.id.toString()),
                    Pair(MessageIndexService.DATA, t.data),
                    Pair(MessageIndexService.TOPIC, t.key.dest.toString()),
                    Pair(MessageIndexService.USER, t.key.from.toString()),
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