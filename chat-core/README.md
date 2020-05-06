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
    * If possible, never see or use NULL's

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

## Domain concepts

## Encoder - dressed up functions

Lets take a look at what Encoder accomplishes in code. Hopefully it is apparent why its useful if needed at all.  

encoder.kt:

    interface Encoder<F, E> {
        fun encode(record: F): E
    }

Primarliy I need a way to exchange JSON objects for core type [T, V, Q] objects, and possibly (experimentally) abstract EMPTY values for **total null safety**.

### object state CAN describe nothing

Empty encoder for a string simply returns "" (nothing). Additionally, a more complicated type such as a UUID consisting of 0's and Int might be 0. In any case, the application should not care
what the non-value is. It should not be looking for a non-value. It is up to the application behaviour to respond when a violation occurs. Null tends to leak. this mean anyone building additional code up stack
will have to determine when null is occured, then factor in a response to it. For example:

ExampleNull.kt:

  val res = service.getOne()

  if(res.total!=null)
    return res.total.accumulators

Kotlin does a good job of enforcing null safety. It is described in the [documentation](https://kotlinlang.org/docs/tutorials/kotlin-for-py/null-safety.html).

## Metadata of Hazards

Being mindful about data shape with as little overlap as possible. In this project, I try to be mindful
about data shapes and attempt as little as possible. Inheritance takes precedence over composition since there'll be many service/persistence specific implementations. This helps keep things tidy.

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

Since Key service is discreet, any one service needing Key<T> would need apply. Persistence-specific subclasses of Key must retain shape of a T but also have its own metadata fields - yay :).
Lets see what a revised interface, and the additional persistence-specific subclass look like.

key.kt:

    interface Key<T> {
        val id: T

CassandraKey.kt:

    data class CassandraKey<T>(
            override val id: T,
            val kind: String
    ) : Key<T>

Key consumers typically need just the ID, but in some issues we also want also to add metadata. Realistically we can implement metadata at site but that means more custom non shareable code. Lets 
see what the service actually does:

KeySErviceCassandra.kt:

    class KeyServiceCassandra<T>(private val template: ReactiveCassandraTemplate,
                                 private val keyGen: Encoder<Unit, T>) : IKeyService<T> {
        override fun <K> key(kind: Class<K>): Mono<out Key<T>> = template
            .insert(CSKey(keyGen.encode(Unit), kind.simpleName))
        
        

## Type Serialization

Serialization says given an object state A, encode it into some representation that can be de-coded to arrive at an object with the same 'shape' or state. Some examples are JVM, ProtoBuf, JSON just to name a few.

What matters most is that we can describe our objects within this serialization format. Since I am tied to JSON AND I have polymorphic types - interfaces with alternate implementations - its best if I issue some hints to the Serializer about how to describe object state.

key.kt:

    @JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
    @JsonTypeName("Key")
    @JsonSubTypes(JsonSubTypes.Type(MessageKey::class))
    interface Key<T> {
        val id: T

Jackson requires @JsonTypeInfo and @JsonSubTypes when it's expected to deal with polymorphic objects.
We then need to supply which types will be subclasses in @JsonSubTypes.

The @JsonTypeInfo annotations configures JSON serialization constraints for an object. In this case we want a wrapper object using Type name "Key" (Defined with @JsonTypeName) to emit an object looking like:

    {"Key":{"id":"1"}}

Thats it for our Serialization concerns...

### Warni**ng

Be careful with JsonTypeInfo.Id.CLASS or any type of identifier which leaks class specifics,
because it has been known as a direct target for malware (aka serializer Gadget).

## De-serialization

Lets take a type such as 'MessageKey' and attempt to deserialize it:

We need to write a Serializer that will handle the type as described in interface MessageKey.kt:

    @JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
    interface MessageKey<T> : Key<T> {
        val from: T
        val dest: T
        val timestamp: Instant

According to the configuration of Serialization in the previous section, a MessageKey will be serialized to JSON as such:

MessageKey.json:

    {
        "Key": {
            "dest": "9876",
            "from": "1234",
            "id": "1",
            "timestamp": "2020-04-06 1919:11:55:00Z"
        }
    }

Since it emitted a 'Key', an ordinary configuration would error at the sight of a different object. Afterall, Key doesnt have the 'dest','from' or 'timestamp' fields. We need to deserialize step by step  until we have enough object state to return.

Deserializers.kt:

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

Next, we have to get these Deserializers loaded into the Spring ApplicationContext's ObjectMapper.

### Jackson Modules

Jackson modules allow us to register serializers and deserializers. In this case, we have our own deserializers for our polymorphic types. We will compose a modules for each domain-specific deserializer.

Using SimpleModule, we can easily construct our own domain modules and expose them to the application context with @Bean.

JacksonModules.kt:

    open class JacksonModules(private val codecKey: Codec<JsonNode, out Any>,
                          private val codecData: Codec<JsonNode, out Any>) {

    @Bean
    open fun keyModule() = SimpleModule("KeyModule", Version.unknownVersion()).apply {
        addDeserializer(Key::class.java, KeyDeserializer(codecKey))
    }
    ...

Upon app startup, Spring Boot autowires a Jackson2ObjectMapperBuilder, and will scan the classpath for available modules.
