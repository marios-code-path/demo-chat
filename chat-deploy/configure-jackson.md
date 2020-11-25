# Jackson Serialization Configuration

# Rsocket Details

First, setup RSocket and its communications strategy; 
Configure an instance of [RSocketStrategies]() along with
normal application configuration.

```kotlin
@Import(RSocketStrategyAutoConfiguration::class)
class MyApplicationClasss { ... }
```

By importing this class into the configuration of the application,
we can expect that the Jackson Modules will also get consumed and enabled
for further execution within the application.

# Jackson JsonDeserializer

We need to ensure any variant of our domain model can be easily producable from JSON.


# Deserializers

# RSocket - Jackson deserializers

# Ensuring consistent deserialization

# Notes and Links