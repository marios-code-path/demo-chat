+++
date = 2019-06-22
publishDate = 2019-06-22
title = "Querying in the message domain"
description = "A little implementation for cassandra backed messages, their users and app querying capabilities"
toc = true
categories = ["spring","cassandra","data","spring-data", "kotlin"]
tags = ["demo","spring","webflux","cassandra","data","kotlin"]
+++

# Query Strategy

Spring Data Repository programming model is comprised of a set of CRUD operations defined in a Spring Data repository interface.
It allows us to program queries in a way that is domain specific. For instance, we can query our message-by-user
with the following interface.

Reactive queries are similar to classic repository queries, only we wrap our <T> object in a reactive publisher.

MessageRepository.kt:

    interface ChatMessageByUserRepository : ReactiveCassandraRepository<ChatMessageByUser, UUID> {
        fun findByKeyUserId(userId: UUID) : Flux<ChatMessageByUser>
    }

From this last interface, you can see I defined the UUID as the key type even though the key for our message types is a regular type.
The reason behind this is I wanted to take advantage of query methods but not have to fill out the entire Key class every time.

For example, I avoid the following:

    interface ChatMessageByUserRepository : ReactiveCassandraRepository<ChatMessageByUser, ChatMessageByUserKey> {
        fun findByKey(userKey: ChatMessageByUserKey) : Flux<ChatMessageByUser>
    }

As for the rest of the repositories.
    
MessageRepository.kt:

    interface ChatMessageByTopicRepository : ReactiveCassandraRepository<ChatMessageByTopic, UUID> {
        fun findByKeyTopicId(topicId: UUID) : Flux<ChatMessageByTopic>
    }
    
    interface ChatMessageRepository : ChatMessageRepositoryCustom, ReactiveCassandraRepository<ChatMessageById, UUID> {
        fun findByKeyMsgId(id: UUID) : Mono<ChatMessageById>
    }

Following standard repository procedures to produce query modes for data with each Key. 
    
# Repositories

# Custom Repositories with CQL

# Conclusion 
