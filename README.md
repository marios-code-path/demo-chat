# Chat App
## Code Name: ImpactDriver
### (Adventure in Cyber-Space with Microservices)

**Overview:**
Demo Chat is a multifaceted application showcasing microservices, primarily written in Kotlin and utilizing Spring Boot. It's designed to illustrate the principles of 12-Factor apps, Test-Driven Development (TDD), and iterative deployment, leveraging Spring Boot and Kotlin's capabilities. The project serves as an educational platform, elucidating the intricacies of Spring Di, Spring Repositories, and their integration with Kotlin.

**Modules:**

1. **chat-core**: Serves as the foundation for other modules, providing essential domain services and entry points for a chat application. Responsibilities include managing domain super-types, service composition, serialization, CODECs, and foundational tests for downstream modules. It defines service strategies for persistence, indexing, key management, messaging, and security.

2. **chat-service-controller**: Facilitates interfaces as controllers over messaging APIs like R-Socket and STOMP. Currently, it primarily leverages R-Socket for efficient inter-process communication.

3. **chat-service-rsocket**: Acts as the client component adhering to service contracts, implemented using the RSocket protocol.

4. **chat-persistence-cassandra**: Integrates with Cassandra for data binding, demonstrating configuration, connection, and data-type strategies. It includes Cassandra-specific testing using TestContainers.

5. **chat-persistence-xstream**: Implements core services using Redis Streams, backing domain operations and exposing chat-core messaging as a Redis-backed service.

6. **chat-deploy**: Aims to productionize the above modules, encompassing cloud-discovery, monitoring, tracing, and various execution styles for deployment.

**Note:** Each module is a work in progress and subject to change. Detailed documentation for each module will be provided in their respective README.md files.
