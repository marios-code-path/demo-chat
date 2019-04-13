+++
date = 2018-07-05
publishDate = 2018-07-05
title = "Hey, look! Its a chat app!"
description = "A little demonstration of publish-subscribe reactive elements when dealing with CQL/Cassandra as primary datasource"
toc = true
categories = ["spring","cassandra","data","spring-data"]
tags = ["demo","spring","webflux","cassandra","data"]
+++

# The domain 
 
 This application will expose several! endpoints for providing a publish-subscribe, and app-action subset API with JSON/REST as the main method of access.
 Essentially, we have to model what it means to interact with app-specific topics called `chatrooms`, while also abstracting away app-specific user-details, and communications plumbing.
 To start, lets create the model for our user - 
 
 A user is composed of several basic properties and a key that will allow us to store and retrieve from Cassandra with column specific criteria.
 
ChatUser.kt:

    @Table("chat_user")
    data class ChatUser(
        @PrimaryKey
        @PrimaryKeyColumn(type = PrimaryKeyType.PARTITIONED, ordinal = 1)
        var id: UUID,
        val handle: String,
        val name: String,
        val timestamp: Date
    )  

Important to the understanding of our app, are the 'handle' (nickname), and 'id'. In order to search on one key,
we will model our data around which property that is indexed.  Because we will search by handle, lets add a new entity
that finds rows given a specific handle.

ChatUserHandle.kt:

@Table("chat_user_handle")
data class ChatUserByHandle(
        var id: UUID,
        @PrimaryKey
        @PrimaryKeyColumn(type = PrimaryKeyType.PARTITIONED, ordinal = 1)
        val handle: String,
        val name: String,
        val timestamp: Date
)

This `ChatUser` model will use `handle` as it's primary key.


## Getting Started

* [WebFlux](https://docs.spring.io/spring/docs/5.0.0.BUILD-SNAPSHOT/spring-framework-reference/html/web-reactive.html)
* [Reactive Security 5](https://spring.io/blog/2017/10/04/spring-tips-reactive-spring-security)
* [lombok](https://projectlombok.org)

# Review

This brief overview should set you up for engaging the ServerHttpSecurity components. Following it's fluent API is a breeze once we get to know the components that we visit.
