# Up and Running (With Cassandra)

This module performs Key, Persistence and maybe Index operations backed by Apache Cassandra.

# Spring-Boot and Maven deps

We are including the [service-persistence-cassandra](http://www.google.com) module which holds transitive dependencies on
[datastax-oss](https://docs.datastax.com/en/developer/java-driver/4.9/manual/mapper/) drivers as well as [spring-data-cassandra]().

Specific to spring-data-cassandra deps is the 'cassandra-driver.version' property which we will
set to a newer version of datastax 4.x series drivers. I'm using '4.9' since it's the latest
as of this writing. Switching versions may cause instability or break the app - be careful
which version you choose!

Showing Maven dependencies for Cassandra Persistence:

```xml
<project>
...
    <properties>
            <spring-boot.version>2.3.0.RELEASE</spring-boot.version>
            <cassandra-driver.version>4.9.0</cassandra-driver.version>
    </properties>
...
    <depenencies>
        ...       
		<dependency>
			<groupId>com.datastax.oss</groupId>
			<artifactId>java-driver-core</artifactId>
			<version>${cassandra-driver.version}</version>
		</dependency>
		<dependency>
			<groupId>com.datastax.oss</groupId>
			<artifactId>java-driver-query-builder</artifactId>
			<version>${cassandra-driver.version}</version>
		</dependency>
		<dependency>
			<groupId>com.datastax.oss</groupId>
			<artifactId>java-driver-mapper-runtime</artifactId>
			<version>${cassandra-driver.version}</version>
		</dependency>
		<dependency>
			<groupId>com.datastax.oss</groupId>
			<artifactId>native-protocol</artifactId>
			<version>1.4.11</version>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-data-cassandra-reactive</artifactId>
			<version>${spring-boot.version}</version>
		</dependency>
    </depenencies>
</project>
```
# Configuring Cassandra with Spring

## CassandraProperties

Take a quick peek at the [Cassandra Properties]() to indicate our server connection.
Note that contact points are the 'gateway' into your cassandra cluster and are not 
the ONLY server on the cluster that are connected - but it is the first.

In this application, we have simple application properties, and I even choose to send network related ones
during execution sequences with application arguments. Alternatively, we can consume cassandra connection details
from Consul or ConfigMaps, but that is a topic of later discourse.

Showing default cassandra properties:
```properties
spring.data.cassandra.schema-action=create_if_not_exists
spring.data.cassandra.request.consistency=one
spring.data.cassandra.request.serial-consistency=any
spring.data.cassandra.keyspace-name=chat
spring.data.cassandra.base-packages=com.demo.chat.repository.cassandra
#spring.data.cassandra.contact-points=some-host
```

## Using Datastax Astra Secure Connect Bundle

You can skip this section if the following is not applicable.

In this application, using a local datacenter for cassandra is nuts - it takes a lot to manage
and deploy. In that case I use DataStax's Cassandra-as-a-service [Astra](https://www.datastax.com/products/datastax-astra) as
it offers a great wealth of flexibility and scales - we won't get to discuss here. One thing
to note is that connection is slightly ( ok a lot) different from a known contact-point. So let's
take a look.

Download secure-connect.zip archive and store it in a known location that is readable
by the process running the app. Next, lets take a look at code configuration to enable 
our datastax driver to consume this bundle:

Not all configurations get created equal:

```kotlin
class AstraConfiguration(
        val props: CassandraProperties,
        val connectPath: String,
)  : AbstractCassandraConfiguration() {

    override fun getSessionBuilderConfigurer(): SessionBuilderConfigurer =
            SessionBuilderConfigurer { sessionBuilder ->
                sessionBuilder
                        .withCloudSecureConnectBundle(Paths.get(connectPath))
                        .withAuthCredentials(props.username, props.password)
            }

    @Bean
     fun driverConfigLoaderCustomizer() = DriverConfigLoaderBuilderCustomizer {
        it.without(DefaultDriverOption.CONTACT_POINTS)
    }

    override fun getKeyspaceName(): String {
        return props.keyspaceName
    }
}
```

OK, so first things first - declare your [AbstractCassandraConfiguration]() then override a few key methods.
The method that lets us declare our secure bundle is 'getSessionBuilderConfigurer'. This gives us
a way to alter the existing [SessionBuilder]() and provide it additional cluster discovery details
for accessing the secure-connect-bundle and providing the username and password to connect to the cluster.

Next, we need to define a bean that customizes [DriverConfigLoader]() to not use any 'contact-points' since
the secure-connect-bundle will get activated. Otherwise, the driver has no idea which to use, or at worst
(as in the 4.6 driver series) the application won't work at all.

## Where config properties can get loaded

Discuss: ConfigMap, Consul key-service in Spring Cloud


# Next Objectives

Strategy for Persistence, Key, and Index ( if we choose Index )

## Links!

[Spring Boot - Creating Efficient Docker Images](https://spring.io/blog/2020/08/14/creating-efficient-docker-images-with-spring-boot-2-3)

[Spring Boot Data Cassandra](https://docs.spring.io/spring-data/cassandra/docs/current/reference/html/#reference)

[A Very Good Tutorial](https://tanzu.vmware.com/developer/blog/save-your-stack-build-cloud-native-apps-with-spring-kubernetes-and-cassandra/)

[Datastax 4.9 Driver](https://docs.datastax.com/en/developer/java-driver/4.9/manual/mapper/)

[Bellsoft Liberica Buildpack for JVM](https://github.com/paketo-buildpacks/bellsoft-liberica)

[Environment BuildPack](https://github.com/paketo-buildpacks/environment-variables)

[OCI Image Spec](https://github.com/opencontainers/image-spec/blob/master/config.md)

[Maven Git-Commit-id plugin](https://github.com/git-commit-id/git-commit-id-maven-plugin)

[Spring - Service Registration and Discovery](https://spring.io/guides/gs/service-registration-and-discovery/)

[GraalVM Spring-Boot](https://github.com/spring-projects-experimental/spring-graalvm-native)