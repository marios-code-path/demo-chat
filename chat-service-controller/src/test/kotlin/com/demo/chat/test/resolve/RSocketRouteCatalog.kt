package com.demo.chat.test.resolve

import com.demo.chat.test.route.Catalog.entry
import com.demo.chat.test.route.Catalog.notIdentity
import com.demo.chat.test.route.Catalog.query
import com.demo.chat.test.route.Catalog.registry
import com.demo.chat.test.route.Catalog.resolve
import com.demo.chat.test.route.Catalog.resolver
import com.demo.chat.test.route.Catalog.service
import com.demo.chat.test.route.Catalog.verify
import com.demo.chat.test.route.CatalogEntry

/**
 * The RSocket route verification catalog. See `CHAT-avduuqwp`, T4 step 7 and
 * review correction 2.
 *
 * Each entry names one handler and every value of its input that the
 * discovery reported, with a domain and a verification path. A value that is
 * no key and no id carries `notIdentity` and a reason. An entry with no field
 * is one where the discovery reported nothing. `RouteSignatureGuardTests` compares this list
 * with the handlers that reflection finds, so a new or changed handler fails
 * until it is classified.
 *
 * The catalog proves that each route is named and classified. It does not
 * prove that a registry read ran. The boundary tests prove that.
 */
object RSocketRouteCatalog {
    val entries: List<CatalogEntry> = listOf(
        // index.authmetadata.add
        entry("AuthMetaIndexController", "addRoute", "AuthMetadata<T>",
            "entity.expires" to notIdentity("an expiry time in milliseconds"),
            "entity.key" to verify("AUTH_METADATA"),
            "entity.principal" to verify("any"),
            "entity.target" to verify("any")),
        // index.authmetadata.add
        entry("CassandraAuthMetaIndexController", "addRoute", "AuthMetadata<T>",
            "entity.expires" to notIdentity("an expiry time in milliseconds"),
            "entity.key" to verify("AUTH_METADATA"),
            "entity.principal" to verify("any"),
            "entity.target" to verify("any")),
        // index.authmetadata.query
        entry("AuthMetaIndexController", "findBy", "IndexSearchRequest"),
        // index.authmetadata.query
        entry("CassandraAuthMetaIndexController", "findBy", "Map<String,String>"),
        // index.authmetadata.rem
        entry("AuthMetaIndexController", "remRoute", "VerifiedKey<T>",
            "key" to resolver("AUTH_METADATA")),
        // index.authmetadata.rem
        entry("CassandraAuthMetaIndexController", "remRoute", "VerifiedKey<T>",
            "key" to resolver("AUTH_METADATA")),
        // index.authmetadata.unique
        entry("AuthMetaIndexController", "findUnique", "IndexSearchRequest"),
        // index.authmetadata.unique
        entry("CassandraAuthMetaIndexController", "findUnique", "Map<String,String>"),
        // index.kv.add
        entry("CassandraKeyValueIndexController", "addRoute", "KeyValuePair<T,E>",
            "entity.data" to notIdentity("a payload value"),
            "entity.key" to verify("KEY_VALUE_PAIR")),
        // index.kv.add
        entry("KeyValueIndexController", "addRoute", "KeyValuePair<T,E>",
            "entity.data" to notIdentity("a payload value"),
            "entity.key" to verify("KEY_VALUE_PAIR")),
        // index.kv.query
        entry("CassandraKeyValueIndexController", "findBy", "Map<String,String>"),
        // index.kv.query
        entry("KeyValueIndexController", "findBy", "IndexSearchRequest"),
        // index.kv.rem
        entry("CassandraKeyValueIndexController", "remRoute", "VerifiedKey<T>",
            "key" to resolver("KEY_VALUE_PAIR")),
        // index.kv.rem
        entry("KeyValueIndexController", "remRoute", "VerifiedKey<T>",
            "key" to resolver("KEY_VALUE_PAIR")),
        // index.kv.unique
        entry("CassandraKeyValueIndexController", "findUnique", "Map<String,String>"),
        // index.kv.unique
        entry("KeyValueIndexController", "findUnique", "IndexSearchRequest"),
        // index.message.add
        entry("CassandraMessageIndexController", "addRoute", "Message<T,E>",
            "entity.data" to notIdentity("a payload value"),
            "entity.key" to verify("MESSAGE"),
            "entity.key.dest" to resolve("MESSAGE_TOPIC"),
            "entity.key.from" to resolve("USER")),
        // index.message.add
        entry("MessageIndexController", "addRoute", "Message<T,E>",
            "entity.data" to notIdentity("a payload value"),
            "entity.key" to verify("MESSAGE"),
            "entity.key.dest" to resolve("MESSAGE_TOPIC"),
            "entity.key.from" to resolve("USER")),
        // index.message.query
        entry("CassandraMessageIndexController", "findBy", "Map<String,String>"),
        // index.message.query
        entry("MessageIndexController", "findBy", "IndexSearchRequest"),
        // index.message.rem
        entry("CassandraMessageIndexController", "remRoute", "VerifiedKey<T>",
            "key" to resolver("MESSAGE")),
        // index.message.rem
        entry("MessageIndexController", "remRoute", "VerifiedKey<T>",
            "key" to resolver("MESSAGE")),
        // index.message.unique
        entry("CassandraMessageIndexController", "findUnique", "Map<String,String>"),
        // index.message.unique
        entry("MessageIndexController", "findUnique", "IndexSearchRequest"),
        // index.topic.add
        entry("CassandraTopicIndexController", "addRoute", "MessageTopic<T>",
            "entity.key" to verify("MESSAGE_TOPIC")),
        // index.topic.add
        entry("TopicIndexController", "addRoute", "MessageTopic<T>",
            "entity.key" to verify("MESSAGE_TOPIC")),
        // index.topic.query
        entry("CassandraTopicIndexController", "findBy", "Map<String,String>"),
        // index.topic.query
        entry("TopicIndexController", "findBy", "IndexSearchRequest"),
        // index.topic.rem
        entry("CassandraTopicIndexController", "remRoute", "VerifiedKey<T>",
            "key" to resolver("MESSAGE_TOPIC")),
        // index.topic.rem
        entry("TopicIndexController", "remRoute", "VerifiedKey<T>",
            "key" to resolver("MESSAGE_TOPIC")),
        // index.topic.unique
        entry("CassandraTopicIndexController", "findUnique", "Map<String,String>"),
        // index.topic.unique
        entry("TopicIndexController", "findUnique", "IndexSearchRequest"),
        // index.user.add
        entry("CassandraUserIndexController", "addRoute", "User<T>",
            "entity.key" to verify("USER")),
        // index.user.add
        entry("UserIndexController", "addRoute", "User<T>",
            "entity.key" to verify("USER")),
        // index.user.query
        entry("CassandraUserIndexController", "findBy", "Map<String,String>"),
        // index.user.query
        entry("UserIndexController", "findBy", "IndexSearchRequest"),
        // index.user.rem
        entry("CassandraUserIndexController", "remRoute", "VerifiedKey<T>",
            "key" to resolver("USER")),
        // index.user.rem
        entry("UserIndexController", "remRoute", "VerifiedKey<T>",
            "key" to resolver("USER")),
        // index.user.unique
        entry("CassandraUserIndexController", "findUnique", "Map<String,String>"),
        // index.user.unique
        entry("UserIndexController", "findUnique", "IndexSearchRequest"),
        // key.exists
        entry("KeyController", "exists", "Key<T>",
            "key" to registry()),
        // key.key
        entry("KeyController", "key", "ChatDomain"),
        // key.rem
        entry("KeyController", "remRoute", "VerifiedKey<T>",
            "key" to resolver("any")),
        // key.rootOf
        entry("KeyController", "rootOfRoute", "Object",
            "id" to registry()),
        // message-recall-global
        entry("MessageRecallController", "recallGlobal", "GlobalRecallRequest"),
        // message-recall-topic
        entry("MessageRecallController", "recallInTopic", "TopicRecallRequest<T>",
            "req.topicId" to query("MESSAGE_TOPIC")),
        // message-recall-user
        entry("MessageRecallController", "recallByUser", "UserRecallRequest<T>",
            "req.userId" to query("USER")),
        // message.message-by-id
        entry("MessageServiceController", "messageById", "ByIdRequest<T>",
            "req.id" to service("MESSAGE")),
        // message.message-listen-topic
        entry("MessageServiceController", "listenTopic", "ByIdRequest<T>",
            "req.id" to service("MESSAGE_TOPIC")),
        // message.message-list-topic
        entry("MessageServiceController", "listMessages", "ByIdRequest<T>",
            "req.id" to service("MESSAGE_TOPIC")),
        // message.message-send
        entry("MessageServiceController", "send", "MessageSendRequest<T,V>",
            "req.dest" to service("MESSAGE_TOPIC"),
            "req.from" to service("USER"),
            "req.msg" to notIdentity("a payload value")),
        // persist.authmetadata.add
        entry("AuthMetaPersistenceController", "addRoute", "AuthMetadata<T>",
            "ent.expires" to notIdentity("an expiry time in milliseconds"),
            "ent.key" to verify("AUTH_METADATA"),
            "ent.principal" to verify("any"),
            "ent.target" to verify("any")),
        // persist.authmetadata.all
        entry("AuthMetaPersistenceController", "all", ""),
        // persist.authmetadata.get
        entry("AuthMetaPersistenceController", "getRoute", "VerifiedKey<T>",
            "key" to resolver("AUTH_METADATA")),
        // persist.authmetadata.key
        entry("AuthMetaPersistenceController", "key", ""),
        // persist.authmetadata.rem
        entry("AuthMetaPersistenceController", "remRoute", "VerifiedKey<T>",
            "key" to resolver("AUTH_METADATA")),
        // persist.keyvalue.add
        entry("KeyValuePersistenceController", "addRoute", "KeyValuePair<T,?>",
            "ent.data" to notIdentity("a payload value"),
            "ent.key" to verify("KEY_VALUE_PAIR")),
        // persist.keyvalue.all
        entry("KeyValuePersistenceController", "all", ""),
        // persist.keyvalue.get
        entry("KeyValuePersistenceController", "getRoute", "VerifiedKey<T>",
            "key" to resolver("KEY_VALUE_PAIR")),
        // persist.keyvalue.key
        entry("KeyValuePersistenceController", "key", ""),
        // persist.keyvalue.rem
        entry("KeyValuePersistenceController", "remRoute", "VerifiedKey<T>",
            "key" to resolver("KEY_VALUE_PAIR")),
        // persist.keyvalue.typedAll
        entry("KeyValuePersistenceController", "typedAll", "Class<E>"),
        // persist.keyvalue.typedByIds
        entry("KeyValuePersistenceController", "typedByIdsRoute", "List<Key<T>>,Class<E>",
            "ids[]" to verify("KEY_VALUE_PAIR")),
        // persist.keyvalue.typedGet
        entry("KeyValuePersistenceController", "typedGetRoute", "Key<T>,Class<E>",
            "key" to verify("KEY_VALUE_PAIR")),
        // persist.membership.add
        entry("MembershipPersistenceController", "addRoute", "TopicMembership<T>",
            "ent.key" to resolve("TOPIC_MEMBERSHIP"),
            "ent.member" to resolve("USER"),
            "ent.memberOf" to resolve("MESSAGE_TOPIC")),
        // persist.membership.all
        entry("MembershipPersistenceController", "all", ""),
        // persist.membership.get
        entry("MembershipPersistenceController", "getRoute", "VerifiedKey<T>",
            "key" to resolver("TOPIC_MEMBERSHIP")),
        // persist.membership.key
        entry("MembershipPersistenceController", "key", ""),
        // persist.membership.rem
        entry("MembershipPersistenceController", "remRoute", "VerifiedKey<T>",
            "key" to resolver("TOPIC_MEMBERSHIP")),
        // persist.message.add
        entry("MessagePersistenceController", "addRoute", "Message<T,E>",
            "ent.data" to notIdentity("a payload value"),
            "ent.key" to verify("MESSAGE"),
            "ent.key.dest" to resolve("MESSAGE_TOPIC"),
            "ent.key.from" to resolve("USER")),
        // persist.message.all
        entry("MessagePersistenceController", "all", ""),
        // persist.message.get
        entry("MessagePersistenceController", "getRoute", "VerifiedKey<T>",
            "key" to resolver("MESSAGE")),
        // persist.message.key
        entry("MessagePersistenceController", "key", ""),
        // persist.message.rem
        entry("MessagePersistenceController", "remRoute", "VerifiedKey<T>",
            "key" to resolver("MESSAGE")),
        // persist.topic.add
        entry("TopicPersistenceController", "addRoute", "MessageTopic<T>",
            "ent.key" to verify("MESSAGE_TOPIC")),
        // persist.topic.all
        entry("TopicPersistenceController", "all", ""),
        // persist.topic.get
        entry("TopicPersistenceController", "getRoute", "VerifiedKey<T>",
            "key" to resolver("MESSAGE_TOPIC")),
        // persist.topic.key
        entry("TopicPersistenceController", "key", ""),
        // persist.topic.rem
        entry("TopicPersistenceController", "remRoute", "VerifiedKey<T>",
            "key" to resolver("MESSAGE_TOPIC")),
        // persist.user.add
        entry("UserPersistenceController", "addRoute", "User<T>",
            "ent.key" to verify("USER")),
        // persist.user.all
        entry("UserPersistenceController", "all", ""),
        // persist.user.get
        entry("UserPersistenceController", "getRoute", "VerifiedKey<T>",
            "key" to resolver("USER")),
        // persist.user.key
        entry("UserPersistenceController", "key", ""),
        // persist.user.rem
        entry("UserPersistenceController", "remRoute", "VerifiedKey<T>",
            "key" to resolver("USER")),
        // pubsub.add
        entry("TopicPubSubController", "openRoute", "T",
            "topicId" to resolve("MESSAGE_TOPIC")),
        // pubsub.exists
        entry("TopicPubSubController", "existsRoute", "T",
            "topic" to resolve("MESSAGE_TOPIC")),
        // pubsub.getByUser
        entry("TopicPubSubController", "getByUserRoute", "T",
            "uid" to resolve("USER")),
        // pubsub.getUsersBy
        entry("TopicPubSubController", "getUsersByRoute", "T",
            "topicId" to resolve("MESSAGE_TOPIC")),
        // pubsub.receiveOn
        entry("TopicPubSubController", "listenToRoute", "T",
            "topic" to resolve("MESSAGE_TOPIC")),
        // pubsub.rem
        entry("TopicPubSubController", "closeRoute", "T",
            "topicId" to resolve("MESSAGE_TOPIC")),
        // pubsub.sendMessage
        entry("TopicPubSubController", "sendMessageRoute", "Message<T,? extends V>",
            "message.data" to notIdentity("a payload value"),
            "message.key" to verify("MESSAGE"),
            "message.key.dest" to resolve("MESSAGE_TOPIC"),
            "message.key.from" to resolve("USER")),
        // pubsub.subscribe
        entry("TopicPubSubController", "subscribeOne", "MemberTopicRequest<T>",
            "req.member" to resolve("USER"),
            "req.topic" to resolve("MESSAGE_TOPIC")),
        // pubsub.unsubscribe
        entry("TopicPubSubController", "unSubscribeOne", "MemberTopicRequest<T>",
            "req.member" to resolve("USER"),
            "req.topic" to resolve("MESSAGE_TOPIC")),
        // pubsub.unSubscribeAll
        entry("TopicPubSubController", "unSubscribeAllRoute", "T",
            "member" to resolve("USER")),
        // pubsub.unSubscribeAllIn
        entry("TopicPubSubController", "unSubscribeAllInRoute", "T",
            "topic" to resolve("MESSAGE_TOPIC")),
        // secrets.add
        entry("SecretsStoreControllerConfiguration", "addRoute", "KeyCredential<T>",
            "keyCredential.data" to notIdentity("a payload value"),
            "keyCredential.key" to verify("USER")),
        // secrets.compare
        entry("SecretsStoreControllerConfiguration", "compareRoute", "KeyCredential<T>",
            "keyCredential.data" to notIdentity("a payload value"),
            "keyCredential.key" to verify("USER")),
        // secrets.get
        entry("SecretsStoreControllerConfiguration", "getRoute", "VerifiedKey<T>",
            "key" to resolver("USER")),
        // topic.topic-add
        entry("TopicServiceController", "addRoom", "ByStringRequest"),
        // topic.topic-by-id
        entry("TopicServiceController", "getRoom", "ByIdRequest<T>",
            "req.id" to service("MESSAGE_TOPIC")),
        // topic.topic-by-name
        entry("TopicServiceController", "getRoomByName", "ByStringRequest"),
        // topic.topic-join
        entry("TopicServiceController", "joinRoom", "MembershipRequest<T>",
            "req.roomId" to service("MESSAGE_TOPIC"),
            "req.uid" to service("USER")),
        // topic.topic-leave
        entry("TopicServiceController", "leaveRoom", "MembershipRequest<T>",
            "req.roomId" to service("MESSAGE_TOPIC"),
            "req.uid" to service("USER")),
        // topic.topic-list
        entry("TopicServiceController", "listRooms", ""),
        // topic.topic-members
        entry("TopicServiceController", "roomMembers", "ByIdRequest<T>",
            "req.id" to query("MESSAGE_TOPIC")),
        // topic.topic-rem
        entry("TopicServiceController", "deleteRoom", "ByIdRequest<T>",
            "req.id" to service("MESSAGE_TOPIC")),
        // user.user-add
        entry("UserServiceController", "addUser", "UserCreateRequest"),
        // user.user-by-handle
        entry("UserServiceController", "findByUsername", "ByStringRequest"),
        // user.user-by-id
        entry("UserServiceController", "findByUserId", "ByIdRequest<T>",
            "req.id" to service("USER")),
    )
}
