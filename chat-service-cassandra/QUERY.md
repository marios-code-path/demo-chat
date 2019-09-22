+++
date = 2019-06-22
publishDate = 2019-06-22
title = "Querying in the message domain"
description = "A little implementation for cassandra backed messages, their users and app querying capabilities"
toc = true
categories = ["spring","cassandra","data","spring-data", "kotlin"]
tags = ["demo","spring","webflux","cassandra","data","kotlin"]
+++

# Configuration Guidelines

First, lets speak about how we plan to configure our application to connect to the backing datasource providing persistence to our data model. Because we are using Cassandra, we can plan to use a few specific configuration properties. Lets look at them here, and discuss a bit in-depth:

ClusterConfigurationCassandra.kt:

    interface ConfigurationPropertiesCassandra {
    	val contactPoints: String
    	val port: Int
    	val keyspace: String
    	val basePackages: String
    }

We have abstracted just the bits we need to talk with the data model parts that our persistence engine knows ab out.  In this case we will connect to a Cassandra cluster given the properties shown above. These are actually derivative of the [AbstractClusterConfiguration](https://docs.spring.io/spring-data/cassandra/docs/current/api/org/springframework/data/cassandra/config/AbstractClusterConfiguration.html) and [AbstractCassandraConfiguration](https://docs.spring.io/spring-data/cassandra/docs/current/api/org/springframework/data/cassandra/config/AbstractCassandraConfiguration.html) bean properties. Knowing where and what uses these properties, lets see what we can extract from their property descriptions.

### App-Specific Cassandra Configuration Properties

| property | description | possible/default values| Spring property |
|---------|-------------|-----------------------|-------------------|
| contactPoints | cluster host | localhost| spring.data.cassandra.contact-points |
| port | host port | 9042 | spring.data.cassandra.port |
| keyspace | database name aka 'KeySpace' | chat | spring.data.cassandra.keyspace-name |
| basePackages | Entity bean definition base-package | com.demo.chat.domain | N/A |
| jmxReporting | report JMX metrics | false | N/A |


These properites can then get fed to our own instance of [AbstractReactiveCassandraConfiguration](https://docs.spring.io/spring-data/cassandra/docs/current/api/org/springframework/data/cassandra/config/AbstractReactiveCassandraConfiguration.html) which gets used to wire up our reactive cassandra cluster connection/driver, provides reactive [session](https://docs.spring.io/spring-data/cassandra/docs/current/api/org/springframework/data/cassandra/ReactiveSession.html) and gives us an instance of [ReactiveCassandraTemplate](https://docs.spring.io/spring-data/cassandra/docs/current/api/org/springframework/data/cassandra/core/ReactiveCassandraTemplate.html).

ClusterConfigurationCassandra.kt:

	class ClusterConfigurationCassandra(val props: ConfigurationPropertiesCassandra) : AbstractReactiveCassandraConfiguration() {
	      override fun getKeyspaceName(): String { ...
	      override fun getContactPoints(): String { ...
	      override fun getPort(): Int { ...
	      override fun getSchemaAction(): SchemaAction { ...
	      override fun getEntityBasePackages: Array<String> { ...
	      override fun cluster(): CassandraClusterFactoryBean {
	      	       val cluster = super.cluster()
		       cluster.setJmxReportingEnabled(props.jmxReporting)
		       return cluster
	      }

	      @Configuration
	      @EnableReactiveCassandraRepositories(basePackages = ["com.demo.chat.repository.cassandra"])
	      class RepositoryConfigurationCassandra
        }
	      
Our own `ClusterConfigurationCassandra` will handle the chore of matching configuratyion properties with the components that needs it. These configuration steps consist of a [CassandraClusterFactoryBean](https://docs.spring.io/spring-data/cassandra/docs/current/api/org/springframework/data/cassandra/config/CassandraClusterFactoryBean.html) which provides the cassandra session and we will need something that tells our application context to figure out repositories in [EnableReactiveCassandraRepositories](https://github.com/spring-projects/spring-data-cassandra/blob/master/src/main/asciidoc/reference/reactive-cassandra-repositories.adoc).

 
# Query Strategy

Spring Data Repository programming model is comprised of a set of CRUD operations defined in a Spring Data repository interface.
It allows us to program queries in a way that is domain specific. For instance, we can query our message-by-user with the following interface.

SampleRepository.kt:

    interface PersonRepository : CrudRepository<Person, Int> {
        find byFirstName(firstName: String): Collection<Person>
    }

Reactive queries are similar to classic repository queries, only we wrap our <T> object in a reactive [publisher](https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html).

MessageRepositories.kt:

    interface ChatMessageByUserRepository : ReactiveCassandraRepository<ChatMessageByUser, UUID> {
        fun findByKeyUserId(userId: UUID) : Flux<ChatMessageByUser>
    }

From this last interface, you can see I defined the UUID as the key type even though the key for our message types is a regular type.
The reason behind this is I wanted to take advantage of query methods but not have to fill out the entire Key class every time.

For example, I avoid the following since the information needed for that ChatMessageByUserKey requries more data than is available before query:

    interface ChatMessageByUserRepository : ReactiveCassandraRepository<ChatMessageByUser, ChatMessageByUserKey> {
        fun findByKey(userKey: ChatMessageByUserKey) : Flux<ChatMessageByUser>
    }

As for the rest of the repositories.
    
MessageRepository.kt:

    interface ChatMessageByTopicRepository : ReactiveCassandraRepository<ChatMessageByTopic, UUID> {
        fun findByKeyTopicId(topicId: UUID) : Flux<ChatMessageByTopic>
    }
    
    interface ChatMessageRepository : ChatMessageRepositoryCustom, ReactiveCassandraRepository<ChatMessageById, UUID> {
        fun findByKeyMsgId(id: UUID) : Mono<ChatMessageById>
    }

With this strategy in mind - however limiting - we can make up for the limitations by using a useful technique in [Spring Data
Custom Repositories](https://docs.spring.io/spring-data/jpa/docs/current/reference/html/#repositories.custom-implementations).

# Custom Repositories with CQL




# Conclusion 

# Helpful links

Something a few years old but worth reading : 
[eBays cassandra tutorial series](https://www.ebayinc.com/stories/blogs/tech/cassandra-data-modeling-best-practices-part-1/)
[part 2 of the series](https://www.ebayinc.com/stories/blogs/tech/cassandra-data-modeling-best-practices-part-2/)

