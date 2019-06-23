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


# Thinking of Data Shape

This part of the tutorial will focus on chat topicMessage data shaping, and access/retrieve operations that espouse the Cassandra design techniques. You can find out more about these methodologies at the datastax website [free video](https://academy.datastax.com/resources/ds220-data-modeling?dxt=blogposting).

The first course of action here is to identify the access methods we will need across our data type - in this case, a topicMessage - and how to issue a reliable key across partition nodes.  In this demo, have selected to use [UUID](https://en.wikipedia.org/wiki/Universally_unique_identifier) as our ID type. The main reason is it' s flexibility when used with distributed, multi-server nodes that do not share a counter (such as SQL's auto-increment). UUID's advantage as a consistent and unique key can be summarized [in Datastax Docs](https://docs.datastax.com/en/archived/cql/3.3/cql/cql_reference/timeuuid_functions_r.html) and [as discussed in this post on StackOverflow](https://stackoverflow.com/questions/17945677/cassandra-uuid-vs-timeuuid-benefits-and-disadvantages). 

# Data Modeling with Kotlin - Co-variant Types

We want to have a single canonical Message shape for our application, then let individual components decide
which properties were needed.  In order to facilitate this, I created a `Message` interface that includes a Key, Value, and Timestmap.

Message.kt:

    interface Message<out K, out V> {
        val key: K
        val value: V
        val visible: Boolean
    }

The `out` keyword tells the JVM that any type of K or V will be a subtype of the specified generic parameter. This gives some sub-type flexibility
when implementing downstream components that make use of the same Super-types. See Kotlin's [Generics and Variance documentation](https://kotlinlang.org/docs/reference/generics.html) discussions for 
more information on this topic.

## Complex Composite Keys 

Furthermore, because there would be multiple Keying strategies, there is a separate Key support interface that 
allowed inclusion of data such as Message ID, Topic ID, User ID, etc... The form of keys used in our Cassandra data implementations 
will make use of each of these Key ID's by giving them specific Key Column annotation ( cluster vs partition ). As the basic message 
will include just it's ID and a timestamp. Lets review the supertype key.

MessageKey.kt:

    interface MessageKey {
        val msgId: UUID
        val timestamp: Instant
    }

I created the following subtype to cover a application 'topic' message key..

TopicMessageKey.kt:

    interface TopicMessageKey : MessageKey {
        override val msgId: UUID
        val topicId: UUID
        override val timestamp: Instant
    }

Finally, a key subtype that included the userId - since most messages will be sourced by actual users.

TextMessageKey.kt:

    interface TextMessageKey : TopicMessageKey {
        val userId: UUID
    }

For the message data, we can include 2 generics - Key type and Value type. I added the visibility flag to indicate whether clients should display whats in the value.

Message.kt:
    
    interface Message<out K, out V> {
        val key: K
        val value: V
        val visible: Boolean
    }
    
Now we have an idea of what our application types will look like, lets model these using Cassandra Data Mapping Methodologies. You may like this [write up on mapping within Spring Data](https://github.com/spring-projects/spring-data-cassandra/blob/master/src/main/asciidoc/reference/mapping.adoc).

## QUICK Crash Course

A nested, sorted map describes the governing data-access strategy working within Cassandra [Column Families](https://academy.datastax.com/units/data-model-and-cql-column-families):

SortedMaps.java:

	SortedMap<RowKey, SortedMap<ColumnKey, ColumnValue>>

This lets us treat our column as a sorted map of a sorted map with the form:
     
     map[rowKey][columnKey] -> columnValue. 

## Dude Wheres My Keys?

With the above mental-model in mind, lets discuss keying characteristics for accessing messages. We find 3 scenarios to work out in our Keys.

| Partition Key | Cluster Key | Sort Order |
|--------------|----------|----------|
| Message-Id   | Message-Id | DESC |
| Topic-Id | Message-Id | DESC |
| USER-Id | Message-Id | DESC |

Thus, for the first strategy, we only need a partition key on the Message-Id. Nothing real fancy here, just simple old distribution across nodes - handy for heavy-read intensity of random-keys.

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

A key annotated with `@PrimaryKeyClass` is used to denote [the class holding our composite types](https://docs.datastax.com/en/archived/cql/3.3/cql/cql_using/useCompositePartitionKeyConcept.html). Similarly each of our cluster and partition keys must be annoated with '@PrimaryKeyColumn'.

As the JAVADOC for `@PrimaryKeyColumn` states:

PrimaryKeyColumn.java:

     * Identifies the annotated field of a composite primary key class as a primary key field that is either a partition or
     * cluster key field. Annotated properties must be either reside in a {@link PrimaryKeyClass} to be part of the
     * composite key or annotated with {@link org.springframework.data.annotation.Id} to identify a single property as
     * primary key column.

This also means that any time we would want to have a composite primary key, it would have to go into a seperate class designated byt the '@PrimaryKeyClass' annotation.

Lets breakdown our Key configuration a little more by looking at `ChatMessageByTopicKey`, as it makes use of both cluster AND partition keys.

ChatMessageByTopicKey.kt:

	@PrimaryKeyClass
	data class ChatMessageByTopicKey(
             @PrimaryKeyColumn(name = "msg_id", type = PrimaryKeyType.CLUSTERED, ordinal = 1)
             override val msgId: UUID,
             @Column("topic_id")
             override val userId: UUID,
             @PrimaryKeyColumn(name = "topic_id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
             override val topicId: UUID,
             override val timestamp: Instant
	) : TextMessageKey

Nothing too different except this time, we called our msg_id as our Cluster Key, and Topic_Id became the Parition Key. What this does is bind each 'topic_id' to a single [[set of nodes in a cluster configuration]], while preserving sort ordering using the message_id timeUUID field.

## Referencing Composite Primary Key Properties

Our standard/cannonical message model will have the following shape. We'll annotate the properties here to avoid duplicating efforts for every sub-class. It's simply a sub-type of the Message<T,K> we described earlier.

ChatMessage.kt:

    open class ChatMessage<T : TextMessageKey>(
            @PrimaryKey
            override val key: T,
            @Column("text")
            override val value: String,
            @Column("visible")
            override val visible: Boolean
    ) : Message<T, String>

This simple base-class contains the necessary metadata for each descendant class - thus we have a single key property, annotated with '@PrimaryKey' annotation. I'll let the Java-Doc describe it's behaviour.

PrimaryKey.java:
	* Identifies the primary key field of the entity, which may be of a basic type or of a type that represents a composite
	* primary key class. This field corresponds to the {@code PRIMARY KEY} of the corresponding Cassandra table. Only one
 	* field in a given type hierarchy may be annotated with this annotation.
 	* Remember, if the Cassandra table has multiple primary key columns, then you must define a class annotated with
 	* {@link PrimaryKeyClass} to represent the primary key!
	* Use {@link PrimaryKeyColumn} in conjunction with {@link Id} to specify extended primary key column properties.

In Summary '@PrimaryKey' is permissible for any single key column property. When you have more than one key column, the backing property must coorespond to a class annotated with '@PrimaryKeyClass'.

## Binding POJO to tables

Lets identify each message sub-class to a backing Cassandra table. Using '@Table' we can easily point Spring Data in the right direction, while specifying our Key Type as the generic parameter to parent class Message.

ChatMessages.kt:

    @Table("chat_message_user")
    class ChatMessageByUser(key: ChatMessageByUserKey,
                            value: String,
                            visible: Boolean) : ChatMessage<ChatMessageByUserKey>(key, value, visible)
    
    @Table("chat_message")
    class ChatMessageById(key: ChatMessageByIdKey,
                          value: String,
                          visible: Boolean) : ChatMessage<ChatMessageByIdKey>(key, value, visible)
    
    @Table("chat_message_topic")
    class ChatMessageByTopic(key: ChatMessageByTopicKey,
                             value: String,
                             visible: Boolean) : ChatMessage<ChatMessageByTopicKey>(key, value, visible)

I feel this gives maximum flexibility when creating or extending any additional access concerns.
   
## How about the DDL?

The following DDL's represent CQL models to our by-message-id message type.

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
        PRIMARY KEY (topic_id, msg_id))
    WITH CLUSTERING ORDER BY (msg_time DESC, msg_id DESC);

If you noticed, I created a compound Key using `topic_id` and `msg_id` that enforces order in cluster and allows us to find all messages for a specific topic in the order it was given.

# Conclusions

This wraps it up for DDL. We can now concentrate on query operations in the next article - TBA-[Cassandra Repositories, or how not to do them](http://www.sudoinit5.com/post/spring-data-cassandra-data-query/)

