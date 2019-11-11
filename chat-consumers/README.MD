# consuming Demo-chat Simple clients

## Key Service

Clients written to interact with Key generating system should work with [ChatKeyService](https://github.com/marios-code-path/demo-chat/blob/master/chat-service/src/main/kotlin/com/demo/chat/service/ChatKeyService.kt) interface semantics.

## Persistence Service

Persistence clients should in terms of [ChatPersistence](https://github.com/marios-code-path/demo-chat/blob/master/chat-service/src/main/kotlin/com/demo/chat/service/ChatPersistence.kt) interface with priority on domain-specific implementations. For example, User Persistence clients should check specific domain relationships in the [User](https://github.com/marios-code-path/demo-chat/blob/master/chat-service/src/main/kotlin/com/demo/chat/domain/User.kt) state spec.  

Currently the domain object specs have an object graph that is tied to the Cassandra data store because compound keys are tied to separate bean containers. This should be refactored as soon as possible to avoid further impact to doewnstream clients.


## Index Service

Any Index clients, like Persistence clients should behave acording to their domain specific object graph. Use [ChatIndexService](https://github.com/marios-code-path/demo-chat/blob/master/chat-service/src/main/kotlin/com/demo/chat/service/ChatIndexService.kt) as a guide to building a client here. 

!!! Index services should only ever emit keys as query result data.

## Password and Auth Services

For Auth, use (AuthService)[] . This is incomplete so come back later.

For Password, user (PasswordStore)[https://github.com/marios-code-path/demo-chat/blob/master/chat-service/src/main/kotlin/com/demo/chat/service/ChatPasswordStore.kt] as it is simple enough to talk to.

## Topic Service

This is a composed service, so it is imporatant to understand operations within each method:

TBD

## R-socket Services

Application specific composed services. This is aimed at exposure to external facing clients.it is imporatant to understand operations within each method:

TBD

## R-socket Services

Application specific composed services. This is aimed at exposure to external facing clients.

Here is a breakdown of the services permitted:

TBD


