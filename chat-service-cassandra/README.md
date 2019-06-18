+++
date = 2018-07-05
publishDate = 2018-07-05
title = "Hey, look! Its a chat app!"
description = "A little implementation for cassandra backed messages, users, and app metadata""
toc = true
categories = ["spring","cassandra","data","spring-data"]
tags = ["demo","spring","webflux","cassandra","data"]
+++

# The domain 
 
This sort of application will provide data seek and storage access by implementing the `chat-service` interfaces dissussed [in this article](http://www.). We will use Reactive extensions to make maximum flexability of program flow-control and threading behaviour among [other concerns.](http://www.sudoinit5.com/service-fluxes).


# That Data Model Over There (TDMMOT)

This part of the tutorial will focus on chat message data modeling, and access/retrieve operations as (described)[http://www.sudoinit5.com/demo-chat]. The first course of action here is to identify the access methods we will need thus we can construct partition-keys using the (composite key)[http://datastax/composite-key] method.

* Retrieve Messages by Message-Id (id field)
* Retrieve by TOPIC-Id (roomId field)
* Retrieve by TOPIC-Id in a specific date-range (timestamp or /uuID field)

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


In order to supply messages in the topics domain, we will create another key that specifies topic_id as the Partition type. Additionally, the timestamp field is turned on as our cluster key. This will provide consistent ordering of messages when browsing them in our app.

ChatMessageTopic.kt:
    
        @Table("chat_message_room")
        data class ChatMessageRoom(
                 @PrimaryKey
                 override val key: ChatMessageRoomKey,
            @Column("text")
            override val value: String,
            override val visible: Boolean
        ) : TextMessage



