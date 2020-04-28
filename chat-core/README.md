# Chat-Core - Core Modules

This module composes most of the underlying object and server-scape for the rest of the
modules to include. The idea is to provide the underlying foundation to implement domain services and entry-points
that give rise to a chat application. This module currently has the responsibility to contain domain-specific 
operations that will be discussed in the sections below.

## Domain Super-Types

Lets describe in Kotlin, how we wish to interact with data flow. As a rule of thumb, I use the following objectives:
 
   
I described a data model in which 
 * Immutable
 * Few Layers
 * Common Operations

 
## Domain-Service Composition

Technically, the chat-core defines 4 service strategies:

* Persistence - store entity V given key T
* Index - index entity V given key T, with Query Q  
* Key - generate and store key of type T
* Messaging - exchange entities of V in a topic T

Additionally, we have coupled compositions for security:

* Authorization - persistence operations for authorization data
* Password - persistence operations for password data

## Super-Type Serialization

## Super-Type CODECs (or anything CODECy)

## Tests for the above

## Base Tests for downstream consumers (??)  
