# Chat-Core - Core Modules

This module composes most of the underlying object and server-scape for the rest of the
modules to include. The idea is to provide the underlying foundation to implement domain services and entry-points
that give rise to a chat application. This module currently has the responsibility to contain domain-specific 
operations that will be discussed in the sections below.

## Core Concerns

Let's describe in Kotlin, how we wish to interact with data flow. As a rule of thumb, I use the following objectives:
   
I described a data model in which

 * Immutable Objects
 * Service as Re-usable Operations
 * Messaging Topics
 * No additional layers beyond Authentication/Authorization
 
## Core Services

Technically, there are several service strategies:

* Key - generate and store key of type T
* Persistence - store entity Vt
* Index - index entity Vt. Query with Q
* Messaging - exchange entities of V in a topic T
* Authorization - authorizes T can perform against another T
* Authentication - principal authentication to access T

In all, there have 3 generic types defined by these requirements:

    * key type is T 
    * value type i V
    * index query is Q

Within these types, we can create the Objects that will operate during steady state.

## Types of Hazards

Creating any Type means being careful about representing data objectives with as little 
overlap as possible. In this project, inheritance takes precedence over composition since this is an OOP language.
Yet, I try to remain focused on understanding where object state gets directed, and when another engineer
will use it. 

Here's an example:

key.kt:

    interface Key {
        id      Int
        kind    String
    }

This is for the most part an example of representing what a key T holds. However,
this also means that everyone exchanging Objects of type Key that 'kind' will need
to tag along. This isn't horrible, but is generally annoying to know - and if left unchecked
can lead to others doing the same and then worse - poor performance and bugs will be a result. 

But who actually needs 'kind'? Who would write an application that makes use of a 'kind'
bit voluntarily and during steady state? In this case 'kind' is just a hazard... Unless
you dont already know what to expect, then the only way to find out is to ask everyone if
T exists... or store it only. So this must be a field we only need during analysis... 

### I decided it's not that bad

Well, each service will not have its own key-store but Key-stores should be used by whoever
needs a Key of the ID type vended by that service. All you have to do is tell it what's 
behind it. so. lets see what a revised interface, and s most optimal persistence subclass
look like.

key.kt:

    interface Key<T> {
        val id: T

CassandraKey.kt:

    data class CassandraKey<T>(
            override val id: T,
            val kind: String
    ) : Key<T>

A type to me has a name, and an objective. Everything about the name should be obviated
in code, while code itself meeting its objective should be detailed and then carefully placed
into the body of such a class. This means consistent naming, SANE typing, no comments should
have to explain why a property exists.

## Type Serialization

Serialization is a somewhat straightforward topic. Given an object state A, translate it into some representation
that can be de-coded to arrive at the same object with state A. Some ideas of this kind of represenation 
can be seen on representation of A such as a blob of bytes as with normal JVM serialization, a Thrift binary,
or even as Text as with JSON.




## Super-Type CODECs (or anything CODECy)

## Tests for the above

## Base Tests for downstream consumers (??)  
