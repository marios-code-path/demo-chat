+++
date = 2019-06-18
publishDate = 2019-06-18
title = "Modeling a chat application message structure with Kotlin and Cassandra"
description = "A little implementation for cassandra backed messages, users, and app metadata"
toc = true
categories = ["spring","cassandra","data","spring-data", "kotlin"]
tags = ["demo","spring","webflux","cassandra","data","kotlin"]
+++

# This Application Needs Data
 
This sort of application will provide data seek and storage access by implementing the [chat messages](https://github.com/marios-code-path/demo-chat/blob/master/chat-service/src/main/kotlin/com/demo/chat/domain/Message.kt) and [chat services](https://github.com/marios-code-path/demo-chat/blob/master/chat-service/src/main/kotlin/com/demo/chat/service/ChatService.kt) interfaces in order to compoase a Cassandra-based data-backend to our application. We will use Reactive extensions to make maximum flexability of program flow-control and threading behaviour among [other concerns.](http://www.sudoinit5.com/service-fluxes).


# That Data Model Over There (TDMMOT)

This part of the tutorial will focus on chat topicMessage data modeling, and access/retrieve operations that espouse the Cassandra design techniques. You can find out more about these methodologies at the datastax website [free video](https://academy.datastax.com/resources/ds220-data-modeling?dxt=blogposting).

The first course of action here is to identify the access methods we will need across our data type - in this case, a topicMessage - and how to issue a reliable key across partition nodes.  In this demo, have selected to use [UUID](https://en.wikipedia.org/wiki/Universally_unique_identifier) as our ID type. The main reason is it' s flexability when used with distributed, multi-server nodes that do not share a counter per data model. UUID's advantage as a consistent and unique key can be summarized [in Datastax Docs](https://docs.datastax.com/en/archived/cql/3.3/cql/cql_reference/timeuuid_functions_r.html) and [as discussed in this post on StackOverflow](https://stackoverflow.com/questions/17945677/cassandra-uuid-vs-timeuuid-benefits-and-disadvantages). 

# Data Modeling with Kotlin - Co-variant Types

We want to have a single canonical Message shape for our application, then let individual components decide
which properties were needed.  In order to facilitate this, I created a `Message` interface that includes a Key, Value, and Timestmap.

ChatMessage.kt

    interface Message<out K, out V> {
        val key: K
        val value: V
        val visible: Boolean
    }

The `out` keyword tells the JVM that any type of K or V will be a subtype of the specified generic parameter. This gives some sub-type flexibility
when implementing downstream components that make use of the same Super-Types. See Kotlin's [co-variant/invariante](https://kotlinlang.org/docs/reference/generics.html) discussions for 
more information on this topic.

## Optimization too soon ? Maybe ?

Furthermore, because early on I knew there would be multiple Keying strategies, I created a separate Key support interface that 
allowed the inclusion of various key data such as Message ID, Topic ID, User ID, etc... The form of keys used in our Cassandra data implementations 
will make use of each of these Key ID's by giving them specific Key Column annotation ( cluster vs partition ). As the basic message 
will include just it's ID and a timestamp. Lets review the super-type key.

MessageKey.kt:

    interface MessageKey {
        val msgId: UUID
        val timestamp: Instant
    }

If I wanted to enforce a canonical topic-level Key, I created the following base type.

TopicMessageKey.kt:

    interface TopicMessageKey : MessageKey {
        override val msgId: UUID
        val topicId: UUID
        override val timestamp: Instant
    }

To enforce User-Id sourced Messages, I created a sub-type that included the userId in addition to other key components.

TextMessageKey.kt:

    interface TextMessageKey : TopicMessageKey {
        val userId: UUID
    }

For the Message body, lets review what we'll deal with for the rest of the application components.

Message.kt:
    
    interface Message<out K, out V> {
        val key: K
        val value: V
        val visible: Boolean
    }
    
Now we have an idea of what our application types will look like, lets model these using Cassandra key methodologies.

## NOSQL Data Modeling with Cassandra - QUICK Crash Course

The following picture describes the basic data-access strategy working in Cassandra [Column Families](https://academy.datastax.com/units/data-model-and-cql-column-families):

SomeMaps.java:

	SortedMap<RowKey, SortedMap<ColumnKey, ColumnValue>>

This lets us treat our column as a sorted map of a sorted map of:
     
     map[rowKey][columnKey] -> columnValue. 

With this picture in mind, lets model the characteristics of our topicMessage keys:

* Messages by MSG-Id
* Messages by TOPIC-Id
* Messages by TOPIC-Id && DATE

Thus, our canonical chat TextMessage (any message sent by a user) will have the following shape: 

ChatMessageById.kt:

    open class ChatMessage<T : TextMessageKey>(
            @PrimaryKey
            override val key: T,
            @Column("text")
            override val value: String,
            @Column("visible")
            override val visible: Boolean
    ) : Message<T, String>

and it's mundane, single partition-key property bearing class.

ChatMessageKey.kt:
    
    @PrimaryKeyClass
    class ChatMessageByIdKey(
            @PrimaryKeyColumn(name = "msg_id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
            override val id: UUID,
            @Column("user_id")
            override val userId: UUID,
            @Column("room_id")
            override val roomId: UUID,
            override val timestamp: Instant
    ) : TextMessageKey
    
This Class must be duplicated for 3 separate indexing strategies: by-id, by-topic, and by-user. Full source to these table-variants is [browse-able here](https://github.com/marios-code-path/demo-chat/blob/master/chat-service-cassandra/src/main/kotlin/com/demo/chat/domain/ChatMessage.kt).

To be brief, lets examine the by-topic topicMessage key variants. We breakdown column and index configuration by looking at `ChatMessageByTopicKey`

ChatMessageByTopicKey.kt:
    
	@PrimaryKeyClass
	data class ChatMessageByTopicKey(
             @PrimaryKeyColumn(name = "msg_id", type = PrimaryKeyType.CLUSTERED, ordinal = 1)
             override val id: UUID,
             @PrimaryKeyColumn(name = "user_id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
             override val userId: UUID,
             @Column("topic_id")
             override val topicId: UUID,
             @PrimaryKeyColumn(name = "msg_time", type = PrimaryKeyType.CLUSTERED, ordinal = 2, ordering = Ordering.DESCENDING)
             override val timestamp: Instant
	) : TextMessageKey


As the JAVADOC for `@PrimaryKeyColumn` states:

PrimaryKeyColumn.java:

     * Identifies the annotated field of a composite primary key class as a primary key field that is either a partition or
     * cluster key field. Annotated properties must be either reside in a {@link PrimaryKeyClass} to be part of the
     * composite key or annotated with {@link org.springframework.data.annotation.Id} to identify a single property as
     * primary key column.

Note that use of `@PrimaryKeyColumn` is specific to our particular use-case wherein `@PrimaryKeyClass` is used to denote [the class holding our composite types](https://docs.datastax.com/en/archived/cql/3.3/cql/cql_using/useCompositePartitionKeyConcept.html) in use on this data type.

ChatMessageByTopicKey.kt:

	@PrimaryKeyClass
	data class ChatMessageByTopicKey(
             @PrimaryKeyColumn(name = "msg_id", type = PrimaryKeyType.CLUSTERED, ordinal = 1)
             override val id: UUID,
             @PrimaryKeyColumn(name = "user_id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
             override val userId: UUID,
             @Column("topic_id")
             override val topicId: UUID,
             @PrimaryKeyColumn(name = "msg_time", type = PrimaryKeyType.CLUSTERED, ordinal = 2, ordering = Ordering.DESCENDING)
             override val timestamp: Instant
	) : TextMessageKey


## How about the DDL?

The following DDL's represent Cassadra creeation of our message types.

simple-message.cql:

    CREATE TABLE chat_message_id (
        msg_id TIMEUUID,
        user_id UUID,
        topic_id UUID,
        text    varchar,
        msg_time timestamp,
        visible Boolean,
        PRIMARY KEY (msg_id, msg_time))
    WITH CLUSTERING ORDER BY (msg_time DESC);

With this DDL statement, we created a basic message with 'msg_id' as it's Partition Key, and the additional `msg_time` field as it's clustering Key (in Descending order).
We are only really interested in accessing rows in this table by individual ID, thus each message may come from one or more nodes.

To enable field indexing by `topic_id`, I created the following table.

simple-message.cql:

    CREATE TABLE chat_message_topic (
        msg_id TIMEUUID,
        user_id UUID,
        topic_id UUID,
        text    varchar,
        msg_time timestamp,
        visible Boolean,
        PRIMARY KEY (topic_id, msg_time, msg_id))
    WITH CLUSTERING ORDER BY (msg_time DESC, msg_id DESC);

If you noticed, I created a compound Key using `topic_id`, `msg_time`, and `msg_id` that enforces order in cluster and allows us to find all messages for a specific topic in the order it was given.
(NOTE: We will eventually fill streams with this data, but just keep that in mind for future articles).

Finally, to enable `user_id` specific indexing, I created the following table:

    CREATE TABLE chat_message_user (
        msg_id TIMEUUID,
        user_id UUID,
        topic_id UUID,
        text    varchar,
        msg_time timestamp,
        visible Boolean,
        PRIMARY KEY (user_id, msg_time, msg_id))
    WITH CLUSTERING ORDER BY (msg_time DESC, msg_id DESC);

This wraps it up for DDL. We can now concentrate on query operations in the next article - [Cassandra Repositories, or how not to do them]()

