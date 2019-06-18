+++
date = 2018-07-05
publishDate = 2018-07-05
title = "Hey, look! Its a chat app!"
description = "A little implementation for cassandra backed messages, users, and app metadata""
toc = true
categories = ["spring","cassandra","data","spring-data"]
tags = ["demo","spring","webflux","cassandra","data"]
+++

# This Application Needs Data
 
This sort of application will provide data seek and storage access by implementing the [chat messages](https://github.com/marios-code-path/demo-chat/blob/master/chat-service/src/main/kotlin/com/demo/chat/domain/Message.kt) and [chat services](https://github.com/marios-code-path/demo-chat/blob/master/chat-service/src/main/kotlin/com/demo/chat/service/ChatService.kt) interfaces in order to compoase a Cassandra-based data-backend to our application. We will use Reactive extensions to make maximum flexability of program flow-control and threading behaviour among [other concerns.](http://www.sudoinit5.com/service-fluxes).


# That Data Model Over There (TDMMOT)

This part of the tutorial will focus on chat message data modeling, and access/retrieve operations that espouse the Cassandra design techniques. You can find out more about these methodologies at the datastax website [free video](https://academy.datastax.com/resources/ds220-data-modeling?dxt=blogposting).

The first course of action here is to identify the access methods we will need across our data type - in this case, a message - and how to issue a reliable key across partition nodes.  In this demo, have selected to use [UUID](https://en.wikipedia.org/wiki/Universally_unique_identifier) as our ID type. The main reason is it' s flexability when used with distributed, multi-server nodes that do not share a counter per data model. UUID's advantage as a consistent and unique key can be summarized [in Datastax Docs](https://docs.datastax.com/en/archived/cql/3.3/cql/cql_reference/timeuuid_functions_r.html) and [as discussed in this post on StackOverflow](https://stackoverflow.com/questions/17945677/cassandra-uuid-vs-timeuuid-benefits-and-disadvantages). 

## But, why multiple data classes?

The following picture describes the basic data-access strategy working in Cassandra Column Families:

SomeMaps.java:

	SortedMap<RowKey, SortedMap<ColumnKey, ColumnValue>>

This lets us treat our column as a sorted map of a sorted map of:
     
     map[rowKey][columnKey] -> columnValue. 

With this picture in mind, lets model the characteristics of our message keys:

* Messages by MSG-Id
* Messages by TOPIC-Id
* Messages by TOPIC-Id && DATE

Thus, our chat message will have the following shape: 

ChatMessage.kt:

    @Table("chat_message")
    data class ChatMessage(
            @PrimaryKey
            override val key: ChatMessageKey,
            @Column("data")
            override val data: String,
            override val visible: Boolean
    ) : TextMessage

In order to satisfy our access requirements, lets plug some key fields into our 'key class' and re-use in the other access scenarios ( topic-Id, Date ). For this use case, I decided to partition on msg_id specifically to address the needs of many random message seeks. While userId and roomId are available on this key, we do so in keeping the contract with TextMessageKey interface.

ChatMessageKey.kt:
    
    @PrimaryKeyClass
    class ChatMessageKey(
            @PrimaryKeyColumn(name = "msg_id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
            override val id: UUID,
            @Column("user_id")
            override val userId: UUID,
            @Column("room_id")
            override val roomId: UUID,
            override val timestamp: Instant
    ) : TextMessageKey

In order to supply messages by topic, we can specify topic_id as Partition key. Additionally, the timestamp field is turned on as our cluster key. This will provide consistent ordering of messages when browsing them in our app.

ChatMessageTopicKey.kt:

	@PrimaryKeyClass
	data class ChatMessageTopicKey(
             @PrimaryKeyColumn(name = "msg_id", type = PrimaryKeyType.CLUSTERED, ordinal = 1)
             override val id: UUID,
             @PrimaryKeyColumn(name = "user_id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
             override val userId: UUID,
             @Column("topic_id")
             override val topicId: UUID,
             @PrimaryKeyColumn(name = "msg_time", type = PrimaryKeyType.CLUSTERED, ordinal = 2, ordering = Ordering.DESCENDING)
             override val timestamp: Instant
	) : TextMessageKey
    
# This Application Needs Query

Use this space to introduce custom Implementations of ReactiveRepositories, Use of CQL for batching operations, and testability of Cassandra using @DataCassandraTest (s).

# Next Step - Configuration

With the vital data pieces lined up, lets take advantage our our (hopefully pluggable) data layer to expose endpoints which consume them as microservices.... On to the [r-socket modules](https://github.com/marios-code-path/demo-chat/tree/master/chat-service-rsocket).


