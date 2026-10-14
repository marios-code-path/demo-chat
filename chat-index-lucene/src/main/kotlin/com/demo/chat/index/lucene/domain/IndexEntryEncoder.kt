package com.demo.chat.index.lucene.domain

import com.demo.chat.domain.*
import com.demo.chat.service.core.KeyValueIndexFields
import com.demo.chat.service.core.MembershipIndexService
import com.demo.chat.service.core.MessageIndexService
import com.demo.chat.service.security.AuthMetaIndex
import com.demo.chat.index.lucene.storage.IndexEntry
import java.util.function.Function


fun interface IndexEntryEncoder<E> : Function<E, List<Pair<String, String>>> {

    companion object Factory {
        /**
         * Encodes the value of a key-value pair, not the pair itself.
         *
         * The pair is only a carrier. The value carries the fields, and the
         * registered [KeyValueIndexFields] for its type names them. The key
         * is added by the index, so it is not repeated here.
         */
        fun <T> ofKeyValueFields(fields: KeyValueIndexFields): IndexEntryEncoder<KeyValuePair<T, Any>> =
            IndexEntryEncoder { t ->
                fields.fieldsOf(t.data).also(IndexEntry::requireNoReservedField)
                    .map { (name, value) -> IndexFieldKinds.KEY_VALUE.field(name, value) }
            }

        fun <T> ofAuthMeta(typeUtil: TypeUtil<T>): IndexEntryEncoder<AuthMetadata<T>> =
            IndexEntryEncoder { t ->
                listOf(
                    IndexFieldKinds.AUTH.field(AuthMetaIndex.PRINCIPAL, typeUtil.toString(t.principal.id)),
                    IndexFieldKinds.AUTH.field(AuthMetaIndex.TARGET, typeUtil.toString(t.target.id)),
                    IndexFieldKinds.AUTH.field(AuthMetaIndex.ID, typeUtil.toString(t.key.id))
                )
            }

        fun <T> ofUser(): IndexEntryEncoder<User<T>> =
            IndexEntryEncoder { t ->
                listOf(
                    IndexFieldKinds.USER.field("key", t.key.id.toString()),
                    IndexFieldKinds.USER.field("handle", t.handle),
                    IndexFieldKinds.USER.field("name", t.name)
                )
            }

        /** A pointer carries no text, so it writes no data field. See `CHAT-erkoxybm`. */
        fun <T> ofMessage(): IndexEntryEncoder<Message<T, String>> =
            IndexEntryEncoder { t ->
                listOfNotNull(
                    IndexFieldKinds.MESSAGE.field(MessageIndexService.ID, t.key.id.toString()),
                    IndexFieldKinds.MESSAGE.field(MessageIndexService.DATA, t.data).takeUnless { t is PointerMessage<*> },
                    IndexFieldKinds.MESSAGE.field(MessageIndexService.TOPIC, t.key.dest.toString()),
                    IndexFieldKinds.MESSAGE.field(MessageIndexService.USER, t.key.from.toString()),
                )
            }

        fun <T> ofTopic(): IndexEntryEncoder<MessageTopic<T>> =
            IndexEntryEncoder { t ->
                listOf(
                    IndexFieldKinds.TOPIC.field("key", t.key.id.toString()),
                    IndexFieldKinds.TOPIC.field("name", t.data)
                )
            }

        fun <T> ofTopicMembership(): IndexEntryEncoder<TopicMembership<T>> =
            IndexEntryEncoder { t ->
                listOf(
                    IndexFieldKinds.MEMBERSHIP.field("key", t.key.toString()),
                    IndexFieldKinds.MEMBERSHIP.field(MembershipIndexService.MEMBER, t.member.toString()),
                    IndexFieldKinds.MEMBERSHIP.field(MembershipIndexService.MEMBEROF, t.memberOf.toString()),
                    IndexFieldKinds.MEMBERSHIP.field(
                        MembershipIndexService.MEMBER_PAIR,
                        MembershipIndexService.pairKeyOf(t.member, t.memberOf),
                    )
                )
            }
    }
}
