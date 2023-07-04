# Jackson Serialization Configuration

# Rsocket Details

First, setup RSocket and its communications strategy; 
Configure an instance of [RSocketStrategies]() along with
normal application configuration.

```kotlin
@Import(RSocketStrategyAutoConfiguration::class)
class MyApplicationClasss { ... }
```

Jackson is usually on the classpath and will get configured accordingly.
You really do not need to do anything more to ser/deser your POJO's or POKO's ( Plain Old Kotlin Objects).

However, in this app we are using interfaces and static constructors to create instances
of objects. Additinally we have subclassed entities that will have specific construction
logic along with nested hierarchies.  

Jackson alone wont be able to ser/deser these kinds of objects.  For that, there are 
custom Deserializers.

# Jackson JsonDeserializer

We need to ensure any variant of our domain model can be easily producable from JSON.


# Deserializers

# RSocket - Jackson deserializers

# Ensuring consistent deserialization

# Notes and Links