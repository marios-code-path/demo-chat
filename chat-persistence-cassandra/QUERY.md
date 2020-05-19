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

First, configure the application to connect to a datasource providing the persistence to our data model. Because we are using Cassandra and Spring supports configuration out of the box,
we can plan to use a few specific configuration properties. Let's look at them here, and discuss a bit in-depth how properties work for us in [org.springframework.boot.autoconfigure.cassandraCassandraProperties.java](https://docs.spring.io/spring-boot/docs/current/api/org/springframework/boot/autoconfigure/cassandra/CassandraProperties.html)

### App-Specific Cassandra Configuration Properties

| property | description | possible/default values| Spring property |
|---------|-------------|-----------------------|-------------------|
| contactPoints | cluster host | localhost| spring.data.cassandra.contact-points |
| port | host port | 9042 | spring.data.cassandra.port |
| keyspace | database name aka 'KeySpace' | chat | spring.data.cassandra.keyspace-name |
| basePackages | Entity bean definition base-package | com.demo.chat.domain | N/A |
| jmxReporting | report JMX metrics | false | N/A |


These properties can be overridden or copied to a custom [AbstractReactiveCassandraConfiguration](https://docs.spring.io/spring-data/cassandra/docs/current/api/org/springframework/data/cassandra/config/AbstractReactiveCassandraConfiguration.html) 
which will provide the reactive [session](https://docs.spring.io/spring-data/cassandra/docs/current/api/org/springframework/data/cassandra/ReactiveSession.html) and gives us an instance of [ReactiveCassandraTemplate](https://docs.spring.io/spring-data/cassandra/docs/current/api/org/springframework/data/cassandra/core/ReactiveCassandraTemplate.html).

ClusterConfigurationCassandra.kt:

    class ClusterConfigurationCassandra(val props: CassandraProperties,
                                        val pack: ConfigurationPropertiesCassandra) : AbstractReactiveCassandraConfiguration() {
    
        override fun getKeyspaceName(): String {
            return props.keyspaceName
        }
    
        override fun getContactPoints(): String {
            return props.contactPoints.get(0)
        }
    
        override fun getPort(): Int {
            return props.port
        }
    
        override fun getSchemaAction(): SchemaAction {
            return SchemaAction.CREATE
        }
    
        override fun getEntityBasePackages(): Array<String> {
            return arrayOf(pack.basePackages)
        }
    
        override fun cluster(): CassandraClusterFactoryBean {
            val cluster = super.cluster()
            cluster.setJmxReportingEnabled(props.isJmxEnabled)
            return cluster
        }
    }
	      
For future deployment situation, our application makes use of [EnableReactiveCassandraRepositories](https://github.com/spring-projects/spring-data-cassandra/blob/master/src/main/asciidoc/reference/reactive-cassandra-repositories.adoc) annotation
to figure out data-repository auto-configuration.

# Query Strategy

Spring Data Repository programming model comprises a set of CRUD operations defined in the base [repository](https://docs.spring.io/spring-data/data-commons/docs/current/reference/html/#repositories) abstraction.
It allows us to program queries in a way that is domain specific. For instance, we can query our message-by-user with the following interface.

SampleRepository.kt:

    interface PersonRepository : CrudRepository<Person, Int> {
        fun findByFirstName(firstName: String): Collection<Person>
    }

Reactive queries are similar to classic repository queries, only we wrap our <Element> object in a reactive [publisher](https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html).

SampleReactiveRepository.kt:

    interface ReactivePersonRepository : ReactiveCrudRepository<Person, Int> {
        fun findByFirstName(firstname: String): Flux<Person>
    }
    
This translates neatly to our own application domain repositories which are covered in the next section.

## Enter the Cassandra Key Repository

Starting with our own [Key](https://github.com/marios-code-path/demo-chat/blob/master/chat-core/src/main/kotlin/com/demo/chat/domain/KeyDataPair.kt) interface, we can model a cassandra type by subclassing:

CassandraEventKey.kt:

    @Table("keys")
    data class CEventKey<T>(
            @PrimaryKeyColumn(name = "id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
            override val id: T,
            val kind: String
    ) : Key<T>

Cluster vs Partition key? Without getting too far into the forest. 
Cassandra uses a token(partition_key) function that determines the server/node of said record. This is then a requirement that every column family have a PARTITION key.
Thus, using partition_key as a sorting concern is an incorrect application of this key type. To apply sorting through column family records on a particular node, we can use a Cluster Key. 


## Custom Query Reasons

limitations are overcommed easily by writing an own implementation in  [Spring Data Custom Repositories](https://docs.spring.io/spring-data/jpa/docs/current/reference/html/#repositories.custom-implementations).

# Custom Repositories with CQL




# Conclusion 

# Helpful links

Something a few years old but worth reading : 
[eBays cassandra tutorial series](https://www.ebayinc.com/stories/blogs/tech/cassandra-data-modeling-best-practices-part-1/)
[part 2 of the series](https://www.ebayinc.com/stories/blogs/tech/cassandra-data-modeling-best-practices-part-2/)

