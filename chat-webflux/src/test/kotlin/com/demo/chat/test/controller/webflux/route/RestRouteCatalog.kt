package com.demo.chat.test.controller.webflux.route

import com.demo.chat.test.route.Catalog.entry
import com.demo.chat.test.route.Catalog.notIdentity
import com.demo.chat.test.route.Catalog.principal
import com.demo.chat.test.route.Catalog.query
import com.demo.chat.test.route.Catalog.resolve
import com.demo.chat.test.route.Catalog.resolver
import com.demo.chat.test.route.Catalog.verify
import com.demo.chat.test.route.CatalogEntry

/**
 * The Rest route verification catalog. See `CHAT-avduuqwp`, T4 step 7 and
 * review correction 2.
 *
 * Each entry names one handler and every value of its input that the
 * discovery reported, with a domain and a verification path. A value that is
 * no key and no id carries `notIdentity` and a reason. An entry with no field
 * is one where the discovery reported nothing. `RestRouteGuardTests` compares this list
 * with the handlers that reflection finds, so a new or changed handler fails
 * until it is classified.
 *
 * The catalog proves that each route is named and classified. It does not
 * prove that a registry read ran. The boundary tests prove that.
 */
object RestRouteCatalog {
    val entries: List<CatalogEntry> = listOf(
        // DELETE /index/auth/rem/{id}
        entry("AuthMetadataIndexRestController", "restRem", "VerifiedKey<T>",
            "id" to resolver("AUTH_METADATA")),
        // DELETE /index/kv/rem/{id}
        entry("KeyValueIndexRestController", "restRem", "VerifiedKey<T>",
            "id" to resolver("KEY_VALUE_PAIR")),
        // DELETE /index/membership/rem/{id}
        entry("MembershipIndexRestController", "restRem", "VerifiedKey<T>",
            "id" to resolver("TOPIC_MEMBERSHIP")),
        // DELETE /index/message/rem/{id}
        entry("MessageIndexRestController", "restRem", "VerifiedKey<T>",
            "id" to resolver("MESSAGE")),
        // DELETE /index/topic/rem/{id}
        entry("TopicIndexRestController", "restRem", "VerifiedKey<T>",
            "id" to resolver("MESSAGE_TOPIC")),
        // DELETE /index/user/rem/{id}
        entry("UserIndexRestController", "restRem", "VerifiedKey<T>",
            "id" to resolver("USER")),
        // DELETE /key/rem/{id}
        entry("IKeyRestController", "restRem", "VerifiedKey<T>",
            "id" to resolver("any")),
        // DELETE /persist/kv/rem/{id}
        entry("KeyValueStoreRestController", "restRem", "VerifiedKey<T>",
            "id" to resolver("KEY_VALUE_PAIR")),
        // DELETE /persist/membership/rem/{id}
        entry("MembershipPersistenceRestController", "restRem", "VerifiedKey<T>",
            "id" to resolver("TOPIC_MEMBERSHIP")),
        // DELETE /persist/message/rem/{id}
        entry("MessagePersistenceRestController", "restRem", "VerifiedKey<T>",
            "id" to resolver("MESSAGE")),
        // DELETE /persist/topic/rem/{id}
        entry("TopicPersistenceRestController", "restRem", "VerifiedKey<T>",
            "id" to resolver("MESSAGE_TOPIC")),
        // DELETE /persist/user/rem/{id}
        entry("UserPersistenceRestController", "restRem", "VerifiedKey<T>",
            "id" to resolver("USER")),
        // DELETE /pubsub/members/{topic}
        entry("PubSubRestController", "restUnSubscribeAllIn", "VerifiedKey<T>",
            "topic" to resolver("MESSAGE_TOPIC")),
        // DELETE /pubsub/pub/{topicId}
        entry("PubSubRestController", "restClose", "VerifiedKey<T>",
            "topicId" to resolver("MESSAGE_TOPIC")),
        // DELETE /pubsub/sub
        entry("PubSubRestController", "restUnSubscribeAll", "ChatUserDetails<T>",
            "userDetails.authorities[]" to notIdentity("a granted authority of the principal"),
            "userDetails.user.key" to principal()),
        // DELETE /pubsub/sub/{id}
        entry("PubSubRestController", "unSubscribeOne", "VerifiedKey<T>,ChatUserDetails<T>",
            "id" to resolver("MESSAGE_TOPIC"),
            "userDetails.authorities[]" to notIdentity("a granted authority of the principal"),
            "userDetails.user.key" to principal()),
        // DELETE /topic/id/{id}
        entry("ChatTopicServiceController", "restDeleteRoom", "VerifiedKey<T>",
            "id" to resolver("MESSAGE_TOPIC")),
        // GET /index/auth/findBy
        entry("AuthMetadataIndexRestController", "findBy", "IndexSearchRequest"),
        // GET /index/auth/findUnique
        entry("AuthMetadataIndexRestController", "findUnique", "IndexSearchRequest"),
        // GET /index/kv/findBy
        entry("KeyValueIndexRestController", "findBy", "IndexSearchRequest"),
        // GET /index/kv/findUnique
        entry("KeyValueIndexRestController", "findUnique", "IndexSearchRequest"),
        // GET /index/membership/findBy
        entry("MembershipIndexRestController", "findBy", "IndexSearchRequest"),
        // GET /index/membership/findUnique
        entry("MembershipIndexRestController", "findUnique", "IndexSearchRequest"),
        // GET /index/message/findBy
        entry("MessageIndexRestController", "findBy", "IndexSearchRequest"),
        // GET /index/message/findUnique
        entry("MessageIndexRestController", "findUnique", "IndexSearchRequest"),
        // GET /index/topic/findBy
        entry("TopicIndexRestController", "findBy", "IndexSearchRequest"),
        // GET /index/topic/findUnique
        entry("TopicIndexRestController", "findUnique", "IndexSearchRequest"),
        // GET /index/user/findBy
        entry("UserIndexRestController", "findBy", "IndexSearchRequest"),
        // GET /index/user/findUnique
        entry("UserIndexRestController", "findUnique", "IndexSearchRequest"),
        // GET /key/exists/{id}
        entry("IKeyRestController", "restExists", "String",
            "id" to resolve("any")),
        // GET /message/id/{id}
        entry("ChatMessageServiceController", "restMessageById", "VerifiedKey<T>",
            "id" to resolver("MESSAGE")),
        // GET /message/list/{id}
        entry("ChatMessageServiceController", "restListMessages", "VerifiedKey<T>",
            "id" to resolver("MESSAGE_TOPIC")),
        // GET /message/topic/{id}
        entry("ChatMessageServiceController", "restListenTopic", "VerifiedKey<T>",
            "id" to resolver("MESSAGE_TOPIC")),
        // GET /persist/kv/all
        entry("KeyValueStoreRestController", "all", ""),
        // GET /persist/kv/byIds
        entry("KeyValueStoreRestController", "restByIds", "List<Key<T>>",
            "ids[]" to verify("KEY_VALUE_PAIR")),
        // GET /persist/kv/get/{id}
        entry("KeyValueStoreRestController", "restGet", "VerifiedKey<T>",
            "id" to resolver("KEY_VALUE_PAIR")),
        // GET /persist/membership/all
        entry("MembershipPersistenceRestController", "all", ""),
        // GET /persist/membership/get/{id}
        entry("MembershipPersistenceRestController", "restGet", "VerifiedKey<T>",
            "id" to resolver("TOPIC_MEMBERSHIP")),
        // GET /persist/message/all
        entry("MessagePersistenceRestController", "all", ""),
        // GET /persist/message/get/{id}
        entry("MessagePersistenceRestController", "restGet", "VerifiedKey<T>",
            "id" to resolver("MESSAGE")),
        // GET /persist/topic/all
        entry("TopicPersistenceRestController", "all", ""),
        // GET /persist/topic/get/{id}
        entry("TopicPersistenceRestController", "restGet", "VerifiedKey<T>",
            "id" to resolver("MESSAGE_TOPIC")),
        // GET /persist/user/all
        entry("UserPersistenceRestController", "all", ""),
        // GET /persist/user/get/{id}
        entry("UserPersistenceRestController", "restGet", "VerifiedKey<T>",
            "id" to resolver("USER")),
        // GET /pubsub/exists/{topic}
        entry("PubSubRestController", "restExists", "VerifiedKey<T>",
            "topic" to resolver("MESSAGE_TOPIC")),
        // GET /pubsub/listen/{topic}
        entry("PubSubRestController", "restListenTo", "VerifiedKey<T>",
            "topic" to resolver("MESSAGE_TOPIC")),
        // GET /pubsub/pub/{topicId}
        entry("PubSubRestController", "restGetUsersBy", "VerifiedKey<T>",
            "topicId" to resolver("MESSAGE_TOPIC")),
        // GET /pubsub/user/{uid}
        entry("PubSubRestController", "restGetByUser", "VerifiedKey<T>",
            "uid" to resolver("USER")),
        // GET /secrets/{id}
        entry("SecretsRestController", "restGetStoredCredentials", "VerifiedKey<T>",
            "id" to resolver("USER")),
        // GET /topic/id/{id}
        entry("ChatTopicServiceController", "restGetRoom", "VerifiedKey<T>",
            "id" to resolver("MESSAGE_TOPIC")),
        // GET /topic/list
        entry("ChatTopicServiceController", "listRooms", ""),
        // GET /topic/members/{id}
        entry("ChatTopicServiceController", "restRoomMembers", "VerifiedKey<T>",
            "id" to resolver("MESSAGE_TOPIC")),
        // GET /topic/name/{name}
        entry("ChatTopicServiceController", "getRoomByName", "ByStringRequest"),
        // GET /user/handle/{name}
        entry("ChatUserServiceController", "findByUsername", "ByStringRequest"),
        // GET /user/id/{id}
        entry("ChatUserServiceController", "restFindByUserId", "VerifiedKey<T>",
            "id" to resolver("USER")),
        // POST /key/new
        entry("IKeyRestController", "restKey", "DomainRequest"),
        // POST /message/recall/global
        entry("ChatMessageRecallController", "recallGlobal", "GlobalRecallRequest"),
        // POST /message/recall/topic
        entry("ChatMessageRecallController", "recallInTopic", "TopicRecallRequest<T>",
            "req.topicId" to query("MESSAGE_TOPIC")),
        // POST /message/recall/user
        entry("ChatMessageRecallController", "recallByUser", "UserRecallRequest<T>",
            "req.userId" to query("USER")),
        // POST /message/send/{id}
        entry("ChatMessageServiceController", "restSend", "VerifiedKey<T>,String,ChatUserDetails<T>",
            "details.authorities[]" to notIdentity("a granted authority of the principal"),
            "details.user.key" to principal(),
            "id" to resolver("MESSAGE_TOPIC")),
        // POST /persist/kv/key
        entry("KeyValueStoreRestController", "key", ""),
        // POST /persist/membership/key
        entry("MembershipPersistenceRestController", "key", ""),
        // POST /persist/message/key
        entry("MessagePersistenceRestController", "key", ""),
        // POST /persist/topic/key
        entry("TopicPersistenceRestController", "key", ""),
        // POST /persist/user/key
        entry("UserPersistenceRestController", "key", ""),
        // POST /pubsub/pub/{topicId}
        entry("PubSubRestController", "restOpen", "VerifiedKey<T>",
            "topicId" to resolver("MESSAGE_TOPIC")),
        // POST /pubsub/send/{topic}
        entry("PubSubRestController", "sendRestMessage", "VerifiedKey<T>,String,ChatUserDetails<T>",
            "topic" to resolver("MESSAGE_TOPIC"),
            "user.authorities[]" to notIdentity("a granted authority of the principal"),
            "user.user.key" to principal()),
        // POST /pubsub/sub/{id}
        entry("PubSubRestController", "subscribeOne", "VerifiedKey<T>,ChatUserDetails<T>",
            "id" to resolver("MESSAGE_TOPIC"),
            "userDetails.authorities[]" to notIdentity("a granted authority of the principal"),
            "userDetails.user.key" to principal()),
        // POST /secrets/compare/{id}
        entry("SecretsRestController", "restCompareSecret", "VerifiedKey<T>,String",
            "id" to resolver("USER")),
        // POST /topic/new
        entry("ChatTopicServiceController", "addRoom", "ByStringRequest"),
        // POST /user/new
        entry("ChatUserServiceController", "addUser", "UserCreateRequest"),
        // PUT /index/auth/add
        entry("AuthMetadataIndexRestController", "restAdd", "AuthMetadata<T>",
            "entity.expires" to notIdentity("an expiry time in milliseconds"),
            "entity.key" to verify("AUTH_METADATA"),
            "entity.principal" to verify("any"),
            "entity.target" to verify("any")),
        // PUT /index/kv/add
        entry("KeyValueIndexRestController", "restAdd", "KeyValuePair<T,E>",
            "entity.data" to notIdentity("a payload value"),
            "entity.key" to verify("KEY_VALUE_PAIR")),
        // PUT /index/membership/add
        entry("MembershipIndexRestController", "restAdd", "TopicMembership<T>",
            "entity.key" to resolve("TOPIC_MEMBERSHIP"),
            "entity.member" to resolve("USER"),
            "entity.memberOf" to resolve("MESSAGE_TOPIC")),
        // PUT /index/message/add
        entry("MessageIndexRestController", "restAdd", "Message<T,E>",
            "entity.data" to notIdentity("a payload value"),
            "entity.key" to verify("MESSAGE"),
            "entity.key.dest" to resolve("MESSAGE_TOPIC"),
            "entity.key.from" to resolve("USER")),
        // PUT /index/topic/add
        entry("TopicIndexRestController", "restAdd", "MessageTopic<T>",
            "entity.key" to verify("MESSAGE_TOPIC")),
        // PUT /index/user/add
        entry("UserIndexRestController", "restAdd", "User<T>",
            "entity.key" to verify("USER")),
        // PUT /persist/kv/add
        entry("KeyValueStoreRestController", "addKv", "KVRequest",
            "req.data" to notIdentity("a payload value"),
            "req.key" to resolve("KEY_VALUE_PAIR")),
        // PUT /persist/membership/add
        entry("MembershipPersistenceRestController", "addMembership", "MembershipRequest<T>",
            "req.roomId" to resolve("MESSAGE_TOPIC"),
            "req.uid" to resolve("USER")),
        // PUT /persist/message/add
        entry("MessagePersistenceRestController", "addMessage", "MessageSendRequest<T,V>",
            "req.dest" to resolve("MESSAGE_TOPIC"),
            "req.from" to resolve("USER"),
            "req.msg" to notIdentity("a payload value")),
        // PUT /persist/topic/add
        entry("TopicPersistenceRestController", "addTopic", "ByStringRequest"),
        // PUT /persist/user/add
        entry("UserPersistenceRestController", "addUser", "UserCreateRequest"),
        // PUT /secrets/add
        entry("SecretsRestController", "restAddCredential", "String"),
        // PUT /secrets/add/{id}
        entry("SecretsRestController", "restAddCredentialWithId", "VerifiedKey<T>,String",
            "id" to resolver("USER")),
        // PUT /topic/join/{id}
        entry("ChatTopicServiceController", "joinRestRoom", "VerifiedKey<T>,ChatUserDetails<T>",
            "id" to resolver("MESSAGE_TOPIC"),
            "user.authorities[]" to notIdentity("a granted authority of the principal"),
            "user.user.key" to principal()),
        // PUT /topic/leave/{id}
        entry("ChatTopicServiceController", "leaveRestRoom", "VerifiedKey<T>,ChatUserDetails<T>",
            "id" to resolver("MESSAGE_TOPIC"),
            "user.authorities[]" to notIdentity("a granted authority of the principal"),
            "user.user.key" to principal()),
    )
}
