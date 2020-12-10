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

Take a quick peek at the [Cassandra Properties](https://docs.spring.io/spring-boot/docs/current/api/org/springframework/boot/autoconfigure/cassandra/CassandraProperties.html) to indicate our server connection.
Note that contact points are the 'gateway' into your cassandra cluster and are not 
the ONLY server on the cluster that are connected - but it is the first.

In this application, we have simple application properties, and I even choose to send network-related args
during execution sequence. Alternatively, we can consume cassandra connection details from Consul or ConfigMaps, but 
that is a topic of later discourse.

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

You can skip this section if you're not using Astra.

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

OK, so first things first - declare your [AbstractCassandraConfiguration](https://docs.spring.io/spring-data/cassandra/docs/current/api/org/springframework/data/cassandra/config/AbstractCassandraConfiguration.html) then override a few key methods.
The method that lets us declare our secure bundle is 'getSessionBuilderConfigurer'. This gives us
a way to alter the existing [SessionBuilder](https://docs.datastax.com/en/drivers/java/4.0/com/datastax/oss/driver/api/core/session/SessionBuilder.html) and provide it additional cluster discovery details
for accessing the secure-connect-bundle and providing the username and password to connect to the cluster.

Next, we need to define a bean that customizes [DriverConfigLoader](https://docs.datastax.com/en/drivers/java/4.0/com/datastax/oss/driver/api/core/config/DriverConfigLoader.html) to not use any 'contact-points' since
the secure-connect-bundle will get activated. Otherwise, the driver has no idea which to use, or at worst
(as in the 4.6 driver series) the application won't work at all.

# Running DSE local via Docker

So you want to run cassandra locally but not compile and execute it by hand, eh?  Good thing you're using 
all the marbles in that amazing brain of yous.  Here are a few tips on getting the DSE (DataStax Enterprise) 
edition of Cassandra up and running in just a few minutes:

## Configure the DSE instance

First, you must do something about overriding some defaults that will give us 
better flexibility when making changes to the datasets and roles.

Likely, you'll want to add users and do some basic administrivia. We can allow this by telling DSE 
to enable it's internal authentication scheme:

Check out the [datastax/docker-images](https://github.com/datastax/docker-images) repository for easy access to
default configuration templates.

```shell script
~$ cd workspace ; git clone https://github.com/datastax/docker-images.git dse-docker-images
```
 
Create a directory to mount the volume. I used workspace:

```shell script
~$ mkdir workspace/dse-volume
```

Now, to modify `dse.yaml`. The instructions are reproducible, but check out the docs for up-to-date mentions.
For that, just follow the instructions in the [DataStax Documents](https://docs.datastax.com/en/security/6.7/security/Auth/secEnableDseAuthenticator.html).

At this point, you should have made any customizations to DSE operation.

## Deploy DSE 

```shell script
$ docker run -p 9042:9042 -e DS_LICENSE=accept --memory 1g --name my-dse -v PATH_TO_CONFIG:/config -d datastax/dse-server:6.8.6
```

At this point we can now follow the instructions over at the [DataStax Documents](https://docs.datastax.com/en/security/6.7/security/Auth/secCreateRootAccount.html) 
for creating superuser /otheruser accounts. This demo uses a 'chatroot' super-user account similar ( just for conformity to the Astra configuration)

# Next Objectives

Strategy for Persistence, Key, and Index ( if we choose Index )

## Links!

[Spring Boot - Creating Efficient Docker Images](https://spring.io/blog/2020/08/14/creating-efficient-docker-images-with-spring-boot-2-3)

[Spring Boot Data Cassandra](https://docs.spring.io/spring-data/cassandra/docs/current/reference/html/#reference)

[A Very Good Tutorial](https://tanzu.vmware.com/developer/blog/save-your-stack-build-cloud-native-apps-with-spring-kubernetes-and-cassandra/)

[Datastax 4.9 Driver](https://docs.datastax.com/en/developer/java-driver/4.9/manual/mapper/)

[DSE Configuration Templates](https://github.com/datastax/docker-images/tree/master/config-templates)

[This DataStax Blog!](https://www.datastax.com/blog/docker-tutorial)

[Bellsoft Liberica Buildpack for JVM](https://github.com/paketo-buildpacks/bellsoft-liberica)

[Environment BuildPack](https://github.com/paketo-buildpacks/environment-variables)

[OCI Image Spec](https://github.com/opencontainers/image-spec/blob/master/config.md)