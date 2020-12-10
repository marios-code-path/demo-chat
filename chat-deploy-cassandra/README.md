# Up and Running (With Cassandra)

This module performs Key, and Persistence (and maybe Index!) operations against Apache Cassandra.

# Configuring Cassandra with Spring

Discuss: CassandraProperties, AbstractReactive vs Abstract..., 

## Where config properties can get loaded

Discuss: ConfigMap, Consul key-service in Spring Cloud

# Using Datastax Astra Secure Connect Bundle

Discuss: Separating the configuration in profiles, SessionBuilder configuration,
 Caveats of POM.xml cassandra-version which is transitive to the spring-data-cassandra
  module.
  
# Deploying Secure Connect Along BuildPack

Build a build-pack layer that adds our .zip secure-connect file, and places it 
in a known location on the filesystem.

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