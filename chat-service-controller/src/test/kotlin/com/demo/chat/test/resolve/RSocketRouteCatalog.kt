package com.demo.chat.test.resolve

import com.demo.chat.test.route.Catalog.deferred
import com.demo.chat.test.route.Catalog.entry
import com.demo.chat.test.route.Catalog.query
import com.demo.chat.test.route.Catalog.registry
import com.demo.chat.test.route.Catalog.resolve
import com.demo.chat.test.route.Catalog.resolver
import com.demo.chat.test.route.Catalog.service
import com.demo.chat.test.route.Catalog.verify
import com.demo.chat.test.route.CatalogEntry

/**
 * The RSocket route verification catalog. See `CHAT-avduuqwp`, T4 step 7.
 *
 * Each entry names one handler, the keys and ids of its input, the domain of
 * each, and how the route verifies it. An entry with no field takes no key
 * and no id. `RouteSignatureGuardTests` compares this list with the handlers
 * that reflection finds, so a new or changed handler fails until it is named.
 *
 * The catalog proves that each route is named and classified. It does not
 * prove that a registry read ran. The boundary tests prove that.
 */
object RSocketRouteCatalog {
    val entries: List<CatalogEntry> = listOf(
        // index.authmetadata.add
        entry("AuthMetaIndexController", "addRoute", "AuthMetadata",
            "entity.key" to verify("AUTH_METADATA"),
            "entity.principal" to deferred("any", "T6, CHAT-ihbesbmn"),
            "entity.target" to deferred("any", "T6, CHAT-ihbesbmn")),
        // index.authmetadata.add
        entry("CassandraAuthMetaIndexController", "addRoute", "AuthMetadata",
            "entity.key" to verify("AUTH_METADATA"),
            "entity.principal" to deferred("any", "T6, CHAT-ihbesbmn"),
            "entity.target" to deferred("any", "T6, CHAT-ihbesbmn")),
        // index.authmetadata.query
        entry("AuthMetaIndexController", "findBy", "IndexSearchRequest"),
        // index.authmetadata.query
        entry("CassandraAuthMetaIndexController", "findBy", "Map"),
        // index.authmetadata.rem
        entry("AuthMetaIndexController", "remRoute", "VerifiedKey",
            "key" to resolver("AUTH_METADATA")),
        // index.authmetadata.rem
        entry("CassandraAuthMetaIndexController", "remRoute", "VerifiedKey",
            "key" to resolver("AUTH_METADATA")),
        // index.authmetadata.unique
        entry("AuthMetaIndexController", "findUnique", "IndexSearchRequest"),
        // index.authmetadata.unique
        entry("CassandraAuthMetaIndexController", "findUnique", "Map"),
        // index.kv.add
        entry("CassandraKeyValueIndexController", "addRoute", "KeyValuePair",
            "entity.key" to verify("KEY_VALUE_PAIR")),
        // index.kv.add
        entry("KeyValueIndexController", "addRoute", "KeyValuePair",
            "entity.key" to verify("KEY_VALUE_PAIR")),
        // index.kv.query
        entry("CassandraKeyValueIndexController", "findBy", "Map"),
        // index.kv.query
        entry("KeyValueIndexController", "findBy", "IndexSearchRequest"),
        // index.kv.rem
        entry("CassandraKeyValueIndexController", "remRoute", "VerifiedKey",
            "key" to resolver("KEY_VALUE_PAIR")),
        // index.kv.rem
        entry("KeyValueIndexController", "remRoute", "VerifiedKey",
            "key" to resolver("KEY_VALUE_PAIR")),
        // index.kv.unique
        entry("CassandraKeyValueIndexController", "findUnique", "Map"),
        // index.kv.unique
        entry("KeyValueIndexController", "findUnique", "IndexSearchRequest"),
        // index.message.add
        entry("CassandraMessageIndexController", "addRoute", "Message",
            "entity.key" to verify("MESSAGE"),
            "entity.key.dest" to resolve("MESSAGE_TOPIC"),
            "entity.key.from" to resolve("USER")),
        // index.message.add
        entry("MessageIndexController", "addRoute", "Message",
            "entity.key" to verify("MESSAGE"),
            "entity.key.dest" to resolve("MESSAGE_TOPIC"),
            "entity.key.from" to resolve("USER")),
        // index.message.query
        entry("CassandraMessageIndexController", "findBy", "Map"),
        // index.message.query
        entry("MessageIndexController", "findBy", "IndexSearchRequest"),
        // index.message.rem
        entry("CassandraMessageIndexController", "remRoute", "VerifiedKey",
            "key" to resolver("MESSAGE")),
        // index.message.rem
        entry("MessageIndexController", "remRoute", "VerifiedKey",
            "key" to resolver("MESSAGE")),
        // index.message.unique
        entry("CassandraMessageIndexController", "findUnique", "Map"),
        // index.message.unique
        entry("MessageIndexController", "findUnique", "IndexSearchRequest"),
        // index.topic.add
        entry("CassandraTopicIndexController", "addRoute", "MessageTopic",
            "entity.key" to verify("MESSAGE_TOPIC")),
        // index.topic.add
        entry("TopicIndexController", "addRoute", "MessageTopic",
            "entity.key" to verify("MESSAGE_TOPIC")),
        // index.topic.query
        entry("CassandraTopicIndexController", "findBy", "Map"),
        // index.topic.query
        entry("TopicIndexController", "findBy", "IndexSearchRequest"),
        // index.topic.rem
        entry("CassandraTopicIndexController", "remRoute", "VerifiedKey",
            "key" to resolver("MESSAGE_TOPIC")),
        // index.topic.rem
        entry("TopicIndexController", "remRoute", "VerifiedKey",
            "key" to resolver("MESSAGE_TOPIC")),
        // index.topic.unique
        entry("CassandraTopicIndexController", "findUnique", "Map"),
        // index.topic.unique
        entry("TopicIndexController", "findUnique", "IndexSearchRequest"),
        // index.user.add
        entry("CassandraUserIndexController", "addRoute", "User",
            "entity.key" to verify("USER")),
        // index.user.add
        entry("UserIndexController", "addRoute", "User",
            "entity.key" to verify("USER")),
        // index.user.query
        entry("CassandraUserIndexController", "findBy", "Map"),
        // index.user.query
        entry("UserIndexController", "findBy", "IndexSearchRequest"),
        // index.user.rem
        entry("CassandraUserIndexController", "remRoute", "VerifiedKey",
            "key" to resolver("USER")),
        // index.user.rem
        entry("UserIndexController", "remRoute", "VerifiedKey",
            "key" to resolver("USER")),
        // index.user.unique
        entry("CassandraUserIndexController", "findUnique", "Map"),
        // index.user.unique
        entry("UserIndexController", "findUnique", "IndexSearchRequest"),
        // key.exists
        entry("KeyController", "exists", "Key",
            "key" to registry()),
        // key.key
        entry("KeyController", "key", "ChatDomain"),
        // key.rem
        entry("KeyController", "remRoute", "VerifiedKey",
            "key" to resolver("any")),
        // key.rootOf
        entry("KeyController", "rootOf", "T",
            "id" to registry()),
        // message-recall-global
        entry("MessageRecallController", "recallGlobal", "GlobalRecallRequest"),
        // message-recall-topic
        entry("MessageRecallController", "recallInTopic", "TopicRecallRequest",
            "req.topicId" to query("MESSAGE_TOPIC")),
        // message-recall-user
        entry("MessageRecallController", "recallByUser", "UserRecallRequest",
            "req.userId" to query("USER")),
        // message.message-by-id
        entry("MessageServiceController", "messageById", "ByIdRequest",
            "req.id" to service("MESSAGE")),
        // message.message-listen-topic
        entry("MessageServiceController", "listenTopic", "ByIdRequest",
            "req.id" to service("MESSAGE_TOPIC")),
        // message.message-send
        entry("MessageServiceController", "send", "MessageSendRequest",
            "req.dest" to service("MESSAGE_TOPIC"),
            "req.from" to service("USER")),
        // persist.authmetadata.add
        entry("AuthMetaPersistenceController", "addRoute", "AuthMetadata",
            "ent.key" to verify("AUTH_METADATA"),
            "ent.principal" to deferred("any", "T6, CHAT-ihbesbmn"),
            "ent.target" to deferred("any", "T6, CHAT-ihbesbmn")),
        // persist.authmetadata.all
        entry("AuthMetaPersistenceController", "all", ""),
        // persist.authmetadata.get
        entry("AuthMetaPersistenceController", "getRoute", "VerifiedKey",
            "key" to resolver("AUTH_METADATA")),
        // persist.authmetadata.key
        entry("AuthMetaPersistenceController", "key", ""),
        // persist.authmetadata.rem
        entry("AuthMetaPersistenceController", "remRoute", "VerifiedKey",
            "key" to resolver("AUTH_METADATA")),
        // persist.keyvalue.add
        entry("KeyValuePersistenceController", "addRoute", "KeyValuePair",
            "ent.key" to verify("KEY_VALUE_PAIR")),
        // persist.keyvalue.all
        entry("KeyValuePersistenceController", "all", ""),
        // persist.keyvalue.get
        entry("KeyValuePersistenceController", "getRoute", "VerifiedKey",
            "key" to resolver("KEY_VALUE_PAIR")),
        // persist.keyvalue.key
        entry("KeyValuePersistenceController", "key", ""),
        // persist.keyvalue.rem
        entry("KeyValuePersistenceController", "remRoute", "VerifiedKey",
            "key" to resolver("KEY_VALUE_PAIR")),
        // persist.keyvalue.typedAll
        entry("KeyValuePersistenceController", "typedAll", "Class"),
        // persist.keyvalue.typedByIds
        entry("KeyValuePersistenceController", "typedByIdsRoute", "List,Class",
            "ids[]" to verify("KEY_VALUE_PAIR")),
        // persist.keyvalue.typedGet
        entry("KeyValuePersistenceController", "typedGetRoute", "Key,Class",
            "key" to verify("KEY_VALUE_PAIR")),
        // persist.membership.add
        entry("MembershipPersistenceController", "addRoute", "TopicMembership",
            "ent.key" to resolve("TOPIC_MEMBERSHIP"),
            "ent.member" to resolve("USER"),
            "ent.memberOf" to resolve("MESSAGE_TOPIC")),
        // persist.membership.all
        entry("MembershipPersistenceController", "all", ""),
        // persist.membership.get
        entry("MembershipPersistenceController", "getRoute", "VerifiedKey",
            "key" to resolver("TOPIC_MEMBERSHIP")),
        // persist.membership.key
        entry("MembershipPersistenceController", "key", ""),
        // persist.membership.rem
        entry("MembershipPersistenceController", "remRoute", "VerifiedKey",
            "key" to resolver("TOPIC_MEMBERSHIP")),
        // persist.message.add
        entry("MessagePersistenceController", "addRoute", "Message",
            "ent.key" to verify("MESSAGE"),
            "ent.key.dest" to resolve("MESSAGE_TOPIC"),
            "ent.key.from" to resolve("USER")),
        // persist.message.all
        entry("MessagePersistenceController", "all", ""),
        // persist.message.get
        entry("MessagePersistenceController", "getRoute", "VerifiedKey",
            "key" to resolver("MESSAGE")),
        // persist.message.key
        entry("MessagePersistenceController", "key", ""),
        // persist.message.rem
        entry("MessagePersistenceController", "remRoute", "VerifiedKey",
            "key" to resolver("MESSAGE")),
        // persist.topic.add
        entry("TopicPersistenceController", "addRoute", "MessageTopic",
            "ent.key" to verify("MESSAGE_TOPIC")),
        // persist.topic.all
        entry("TopicPersistenceController", "all", ""),
        // persist.topic.get
        entry("TopicPersistenceController", "getRoute", "VerifiedKey",
            "key" to resolver("MESSAGE_TOPIC")),
        // persist.topic.key
        entry("TopicPersistenceController", "key", ""),
        // persist.topic.rem
        entry("TopicPersistenceController", "remRoute", "VerifiedKey",
            "key" to resolver("MESSAGE_TOPIC")),
        // persist.user.add
        entry("UserPersistenceController", "addRoute", "User",
            "ent.key" to verify("USER")),
        // persist.user.all
        entry("UserPersistenceController", "all", ""),
        // persist.user.get
        entry("UserPersistenceController", "getRoute", "VerifiedKey",
            "key" to resolver("USER")),
        // persist.user.key
        entry("UserPersistenceController", "key", ""),
        // persist.user.rem
        entry("UserPersistenceController", "remRoute", "VerifiedKey",
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
        entry("TopicPubSubController", "sendMessageRoute", "Message",
            "message.key" to verify("MESSAGE"),
            "message.key.dest" to resolve("MESSAGE_TOPIC"),
            "message.key.from" to resolve("USER")),
        // pubsub.subscribe
        entry("TopicPubSubController", "subscribeOne", "MemberTopicRequest",
            "req.member" to resolve("USER"),
            "req.topic" to resolve("MESSAGE_TOPIC")),
        // pubsub.unsubscribe
        entry("TopicPubSubController", "unSubscribeOne", "MemberTopicRequest",
            "req.member" to resolve("USER"),
            "req.topic" to resolve("MESSAGE_TOPIC")),
        // pubsub.unSubscribeAll
        entry("TopicPubSubController", "unSubscribeAllRoute", "T",
            "member" to resolve("USER")),
        // pubsub.unSubscribeAllIn
        entry("TopicPubSubController", "unSubscribeAllInRoute", "T",
            "topic" to resolve("MESSAGE_TOPIC")),
        // secrets.add
        entry("SecretsStoreControllerConfiguration", "addRoute", "KeyCredential",
            "keyCredential.key" to verify("USER")),
        // secrets.compare
        entry("SecretsStoreControllerConfiguration", "compareRoute", "KeyCredential",
            "keyCredential.key" to verify("USER")),
        // secrets.get
        entry("SecretsStoreControllerConfiguration", "getRoute", "VerifiedKey",
            "key" to resolver("USER")),
        // topic.topic-add
        entry("TopicServiceController", "addRoom", "ByStringRequest"),
        // topic.topic-by-id
        entry("TopicServiceController", "getRoom", "ByIdRequest",
            "req.id" to service("MESSAGE_TOPIC")),
        // topic.topic-by-name
        entry("TopicServiceController", "getRoomByName", "ByStringRequest"),
        // topic.topic-join
        entry("TopicServiceController", "joinRoom", "MembershipRequest",
            "req.roomId" to service("MESSAGE_TOPIC"),
            "req.uid" to service("USER")),
        // topic.topic-leave
        entry("TopicServiceController", "leaveRoom", "MembershipRequest",
            "req.roomId" to service("MESSAGE_TOPIC"),
            "req.uid" to service("USER")),
        // topic.topic-list
        entry("TopicServiceController", "listRooms", ""),
        // topic.topic-members
        entry("TopicServiceController", "roomMembers", "ByIdRequest",
            "req.id" to query("MESSAGE_TOPIC")),
        // topic.topic-rem
        entry("TopicServiceController", "deleteRoom", "ByIdRequest",
            "req.id" to service("MESSAGE_TOPIC")),
        // user.user-add
        entry("UserServiceController", "addUser", "UserCreateRequest"),
        // user.user-by-handle
        entry("UserServiceController", "findByUsername", "ByStringRequest"),
        // user.user-by-id
        entry("UserServiceController", "findByUserId", "ByIdRequest",
            "req.id" to service("USER")),
    )
}
