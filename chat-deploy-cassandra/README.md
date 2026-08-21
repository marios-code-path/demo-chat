# Up and Running (With Cassandra)

This module performs Key, Persistence and Index operations backed by Apache Cassandra.
We'll explain how to start a Cassandra Instance with [DataStax Enterprise](https://www.datastax.com/products/datastax-enterprise), and how to 
connect to your `DSE` instance locally.

# Running DataStax Enterprise in Docker

So you want to run cassandra locally but not compile and execute it by hand, eh?  Good thing you're using 
all the marbles in that amazing brain of yous.  Here are a few tips on getting the DSE (DataStax Enterprise) 
edition of Cassandra up and running in just a few minutes:

## Configuration Overrides for the DSE Instance

First, you must do something about overriding some defaults that will give us 
better flexibility when making changes to the datasets and roles.

Likely, you'll want to add users and do some basic administrivia. We can allow this by overriding 
settings on `dse.yaml` thus telling DSE to enable authorization/euthentaction/RBAC

Check out the [datastax/docker-images](https://github.com/datastax/docker-images) repository for easy access to default configuration templates.

```shell script
~$ cd workspace ; git clone https://github.com/datastax/docker-images.git dse-docker-images
```

The simplest way to perform configuration alteration is to use a [Docker Mount](https://docs.docker.com/storage/bind-mounts/) that the `dse` container 
will look for when pushing configuration updates. In this case, DSE looks for overrides in `/config`.

Create a local (host-machine) directory to mount the volume.

```shell script
~$ mkdir workspace/dse-volume
```

Modify a vanilla `dse.yaml`. The instructions are reproducible, but check out the docs for up-to-date mentions.
Please follow the instructions in the [DataStax Documents](https://docs.datastax.com/en/security/6.7/security/Auth/secEnableDseAuthenticator.html).

At this point, you should have made any customizations to DSE configuration and placed them in your mount directory.

## Deploy DSE 

```shell script
$ docker run -p 9042:9042 -e DS_LICENSE=accept --memory 1g --name my-dse -v PATH_TO_CONFIG:/config -d datastax/dse-server:6.8.6
```

At this point we can now follow the instructions over at the [DataStax Documents](https://docs.datastax.com/en/security/6.7/security/Auth/secCreateRootAccount.html) 
for creating superuser /otheruser accounts. This demo uses a 'chatroot' super-user account.

# App Dependencies (POM.xml)

Lets move on to the Apoplication at hand now. We are including the [service-persistence-cassandra](https://github.com/marios-code-path/demo-chat/tree/master/chat-persistence-cassandra)
module which holds transitive dependencies on [datastax-oss](https://docs.datastax.com/en/developer/java-driver/4.9/manual/mapper/) drivers as well as [spring-data-cassandra](https://spring.io/projects/spring-data-cassandra).

Specific to spring-data-cassandra deps is the `cassandra-driver.version` property which we will
set to a newer version of datastax 4.x series drivers. It is mentioned also because `spring-data-cassandra` uses this property 
to find it's own driver version.

I'm using `4.9` since it's the latest as of this writing. Switching versions may cause instability or break the app,


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

# Connecting Cassandra with Spring

Take a quick peek at the [Cassandra Properties](https://docs.spring.io/spring-boot/docs/current/api/org/springframework/boot/autoconfigure/cassandra/CassandraProperties.html) to indicate our server connection.
Note that contact points are the 'gateway' into your cassandra cluster and are not 
the ONLY server on the cluster that are connected - but it is the first.

In this application, we have simple application properties that can also be sent in as application arguments - a good
strategy when assigning network IPs and passwords at run-time.

  Alternatively, we can consume cassandra connection details from Consul or ConfigMaps, but 
  that is a topic of later discourse.

Showing default cassandra properties:
```properties
spring.data.cassandra.schema-action=create_if_not_exists
spring.data.cassandra.request.consistency=one
spring.data.cassandra.request.serial-consistency=any
spring.data.cassandra.keyspace-name=chat
spring.data.cassandra.base-packages=com.demo.chat.repository.cassandra
spring.data.cassandra.contact-points=some-host-ip
```

## Username Passwords and Contact Points
 
There is an [issue](https://github.com/spring-projects/spring-boot/issues/21487) in Boot 2.3.0, which prevents `cassandra-properties` configured
instances not to pass in the username and password per usual.

To fix this, we can simply configure `SessionBuilder` with [SessionBuilderConfigurer](https://docs.spring.io/spring-data/cassandra/docs/current/api/org/springframework/data/cassandra/config/SessionBuilderConfigurer.html) and tell it the details it's going
to need to connect to the contact point.
                                        
```kotlin
class ContactPointConfiguration(
        val props: CassandraProperties,
) : AbstractCassandraConfiguration() {
    override fun getSessionBuilderConfigurer(): SessionBuilderConfigurer =
            SessionBuilderConfigurer { sessionBuilder ->
                sessionBuilder 
                        .withAuthCredentials(props.username, props.password)
                        .withLocalDatacenter(props.localDatacenter)
            }

    override fun getKeyspaceName(): String {
        return props.keyspaceName
    }
}
```

Doing this will allow our application to work per usual - just send in cassandra properties through application.* or 
as [spring-boot-maven-plugin](https://docs.spring.io/spring-boot/docs/1.1.4.RELEASE/reference/html/build-tool-plugins-maven-plugin.html) command line arguments (e.g. `--spring.data.cassandra.request.serial-consistency=any`).

# Launch the App

This part is simple. just find a script and execute it. Soon, I will add a section for deploying to
K8s environment (probably just a deploy.yaml).  Until then...

## Links!

[Spring Boot - Creating Efficient Docker Images](https://spring.io/blog/2020/08/14/creating-efficient-docker-images-with-spring-boot-2-3)

[Spring Boot Data Cassandra](https://docs.spring.io/spring-data/cassandra/docs/current/reference/html/#reference)

[A Very Good Tutorial](https://tanzu.vmware.com/developer/blog/save-your-stack-build-cloud-native-apps-with-spring-kubernetes-and-cassandra/)

[Datastax 4.9 Driver](https://docs.datastax.com/en/developer/java-driver/4.9/manual/mapper/)

[DSE Configuration Templates](https://github.com/datastax/docker-images/tree/master/config-templates)

[This DataStax Blog!](https://www.datastax.com/blog/docker-tutorial)

[Bellsoft Liberica Buildpack for JVM](https://github.com/paketo-buildpacks/bellsoft-liberica)

[Paketo Buildpack Bindings](https://paketo.io/docs/buildpacks/configuration/#bindings)

[Environment BuildPack](https://github.com/paketo-buildpacks/environment-variables)

[OCI Image Spec](https://github.com/opencontainers/image-spec/blob/master/config.md)