# Chat-Core - Core Modules

This module composes most of the underlying object and server-scape for the rest of the
modules to include. The idea is to provide the underlying foundation to implement domain services and entry-points
that give rise to a chat application. This module currently has the responsibility to contain domain-specific operations that will be discussed in the sections below.

## Core Concerns

Core resembles a complex event processing system - SEDA style. As a rule of thumb, I add the following objectives:

* Immutable Objects
    * Do away with state changes that make apps less predictable and harder to test.
* Service as Re-usable Operations
    * Creative Apps with Core Services

* Endpoint Routing
    * For Partitioned Services

 * Simplicity

## Core Services

Technically, there are several core strategies:

* Key - generate and store key of type T
* Persistence - seek and store entity V
* Index - index entity Vt. Query with Q for T
* Messaging - exchange entities of V in a topic T
* Authorization - authorizes what T can operate on another T
* Authentication - principal authentication yields T for Authorization

In all, there have 3 generic types defined by these services:

    * key type is T 
    * value type V
    * index query Q

Within these types, we can create the Objects that will operate during steady state.

## Types of Hazards

Creating any Type means being careful about representing data objectives with as little overlap as possible. In this project, inheritance takes precedence over composition since this is an OOP language.
Yet, I try to remain focused on understanding where object state gets directed.

Here's an example:

key.kt:

    interface Key {
        id      Int
        kind    String
    }

Everyone exchanging Objects of type Key will need 'kind' to tag along. This isn't horrible, but is generally annoying to know - and if left unchecked can lead to others doing the same and then worse - poor performance and bugs will be a result.

But who actually needs 'kind'? Who would write an application that makes use of a 'kind'
bit voluntarily and during steady state? In this case 'kind' is just a hazard... Unless
you dont already know what to expect, then the only way to know is to ask everyone if
T exists... or store it only and perform regressive analysis to figure out what T actually was for.
So this must be a field we only need during analysis...

### I decided it's not that bad

Each service will not have its own key-store but Key-stores are discreet and should be used by whoever
needs a Key of the ID type vended by that service. Lets see what a revised interface, and the additional persistence-specific subclass look like.

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

Serialization is a somewhat straightforward topic. Given an object state A, translate it into some representation that can be de-coded to arrive at the same object with the same 'shape'. Some examples are JVM, ProtoBuf, JSON just to name a few.

What matters most is that we can describe our objects within this serialization format. Since I am tied to JSON AND I have polymorphic types - interfaces with alternate implementations - its best if I issue some hints to the Serializer about how to describe object state.

key.kt:

    @JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
    @JsonTypeName("Key")
    @JsonSubTypes(JsonSubTypes.Type(MessageKey::class))
    interface Key<T> {
        val id: T

Jackson requires @JsonTypeInfo and @JsonSubTypes when it's expected to deal with polymorphic objects.
We then need to supply which types will be subclasses in @JsonSubTypes.

The @JsonTypeInfo annotations lets us tell the ObjectMapper how to represent our object. In this case
we want a wrapper object with Type name "Key" (Defined with @JsonTypeName) to emit an object looking like:

    "Key":{"id":"1"}

### Warni**ng

Be careful with JsonTypeInfo.Id.CLASS or any type of identifier which leaks class specifics,
because it has been known as a direct target for malware (aka serializer Gadget).

## De-serialization

This process is a bit more detailed in code, but straightforward in process.
Lets take a type such as 'MessageKey' and attempt to deserialize it:

We need to write a Serializer that will handle the type as described in interface MessageKey.kt:

    @JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
    interface MessageKey<T> : Key<T> {
        val from: T
        val dest: T
        val timestamp: Instant

According to the configuration of Serialization in the previous section, a MessageKey will be representated
as such:

MessageKey.json:

    {
        "Key": {
            "dest": "9876",
            "from": "1234",
            "id": "1",
            "timestamp": "2020-04-06 1919:11:55:00Z"
        }
    }

This can be accomplished writing a custom JsonDeserializer which emits Key<T>.
Deserialize a Key<T>:

    class KeyDeserializer<T>(val codec: Codec<JsonNode, T>) : JsonDeserializer<Key<T>>() {
        override fun deserialize(jp: JsonParser?, ctxt: DeserializationContext?): Key<T> {
            val oc: ObjectCodec = jp?.codec!!
            val node: JsonNode = oc.readTree(jp)
    
            val idNode = node.get("id")

Then determine when we have a subtype, or return just the Key:

            return if (node.has("dest") && node.has("from")) {
                val destNode = node.get("dest")
                val fromNOde = node.get("from")
                
                MessageKey.create(codec.decode(idNode), codec.decode(fromNode), codec.decode(destNode))
            }
            else Key.funKey(codec.decode(idNode))
        }
    }

### Jackson Modules

Spring Boot autowires a Jackson2ObjectMapperBuilder, and will scan the classpath for available modules.
Since this bean is what provides the ObjectMapper, we can conduct normal @Bean methods to provide our modules.

## Super-Type CODECs (or anything CODECy)

## Tests for the above

## Base Tests for downstream consumers (??)