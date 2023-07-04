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

The following sections should describe fundamental goals of each module. This is a work
in progress, and as such it is subject to change without notice or reflection in this
document.

## chat-service-controller

Implement interfaces as controllers over Messaging APIS such as R-Socket, STOMP,
or something else entirely. Currently, R-Socket is capable of fulfilling all requirements for inter-process
communication with speed and efficiency. There is no real plan to use something else
unless we really want to demonstrate messaging through STOMP or maybe even gRPC?

## chat-service-rsocket

The client component to the service contracts. This should be implemented with
underlaying protocol - RSocket in this case.

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