# About Demo Chat (Adventures in Cyber Space)

This Application features a compliment of microservice-y components written mostly in Kotlin, using
Spring Boot. This project attempts to distill tenets of 12-Factor, TDD and iterative deployment. I want
to make it known how simple an approach can be had using existing application services on top of
Spring Boot and Kotlin. 

This means the examples are quite more detailed but do teach a lot about the underpinnings of the Spring Di,
Spring Repositories, Spring Service composition and Kotlin interrelation among other things.

Also, I really like communication systems and had the opportunity to develop a multi-user experience that can
be deployed on a whim, used and be educational at the same time. How we get there is what this demo is about.

# The modules, What are these modules?

Each module should eventually get its own README.md, for now here are brief descriptions of each.

## chat-core

This module composes most of the underlying object and server-scape for the rest of the
modules to include. The idea is to provide the underlying foundation to implement domain services and entry-points
that give rise to a chat application. This module currently has the responsibility to convey:

* Domain Super-Types
* Domain-Service Composition
* Super-Type Serialization
* Super-Type CODECs (or anything CODECy)
* Tests for the above
* Base Tests for downstream modules  

Technically, the chat-core defines 4 service strategies:

* Persistence - store entity V given key T
* Index - index entity V given key T, with Query Q  
* Key - generate and store key of type T
* Messaging - exchange entities of V in a topic T 
* Security - authorization, authentication, secrets stores

## chat-service-controller

This module's objective is to turn any of the chat-core domain-services into REST/Rsocket controllers. 

Additionally, this module provides an 'edge' package which specifies chat-specific operations that defines
the chat application as seen by an end user (at the edge). It is most likely that 

## chat-service-rsocket

This module turns the chat-core domain services into R-socket clients. Because this is technology-specific module,
we have the opportunity to test the client against real R-socket controllers with mocked resources.

## chat-persistence-cassandra

This implementation backs chat-core services with cassandra data binding. It shows how to
configure and connect to cassandra and DSE / Astra, and use it's data-type strategies among other things. Inherent to 
this project is the use of testing specific to cassandra with TestContainers.
 
## chat-persistence-xstream

Implementation of core services using Redis Streams. This module uses streams to back domain operations. 

What's more it exposes chat-core/Messaging as a redis-backed service.

## chat-deploy - new!

So, this module attempts to production-ize the modules above. We should be able to 
build and deliver the application to our destination of choice (cloud, local, etc..).

Given this, we will engage cloud-discovery, monitoring, tracing, and execution style (lambda, resource, deploy img).