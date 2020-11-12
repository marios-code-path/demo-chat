# Deployment Objectives

We have several modules to deploy. Each module specifies one microservice.
Arguments and Environment commands shall be sent with deployment artifacts.

These modules will ideally deploy without much fuss, and with uniform interaction from the operator.

## Spring-boot Maven Plugin

[Maven](https://maven.apache.org) is great. Above being a great build tool, Maven lets us process supplemental argument details 
to the deployment artifact ( .jar, container, .tar , etc... ). This is important for issuing essential
program behaviour as close to runtime as possible.

With the [spring-boot-maven-plugin](https://docs.spring.io/spring-boot/docs/current/maven-plugin/reference/html/), specifically here we will add Spring and Buildpack specific properties.


### Passing Arguments to Image 

According to [Environment BuildPack](https://github.com/paketo-buildpacks/environment-variables) docs,
we should be able to inject arguments into a build-pack specific environment (called `BPE_*`).
Passing a `BPE_JAVA_TOOL_OPTIONS` env to the plugin lets us pass program arguments to the image. 
 
POM.xml
```xml
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <mainClass>com.demo.chat.deploy.app.memory.App</mainClass>
                    <image>
                        <name>${appImageName}</name>
                        <cleanCache>false</cleanCache>
                        <env>
                            <BPE_APPEND_JAVA_TOOL_OPTIONS>${env.JAVA_TOOL_OPTIONS}</BPE_APPEND_JAVA_TOOL_OPTIONS>
                        </env>
                    </image>
                    <profiles>
                        <profile>key-service</profile>
                    </profiles>
                </configuration>
        </plugin>
```

NOTE: Make `JAVA_TOOL_OPTIONS` environment variable available at build-time.

## Building the image

Again spring-boot-maven-plugin this easy:

```shell script
mvn spring-boot:build-image
```

In a minute, there should be an image that you can run. We can explore executing containers in the next 
section.

## Debugging image issues:

The docs say much about the buildpacks used to materialize this image:

``Every buildpack-generated image contains an executable called the launcher which can be used to execute a custom command in an environment containing buildpack-provided environment variables. The launcher will execute any buildpack provided profile scripts before running to provided command, in order to set environment variables with values that should be calculated dynamically at runtime.``

``To run a custom start command in the buildpack-provided environment set the ENTRYPOINT to launcher and provide the command using the container CMD.``

Pop into an instance of the container with `sh` and mess around for a bit:

```shell script
docker run -it --entrypoint sh memory-key-service:0.0.1
```

Behold a shell prompt within the cnb container. Neat!

```shell script
$ cd /cnb/lifecycle
$ ls
total 2200
-rwxr-xr-x 1 root root 2252800 Jan  1  1980 launcher
$ export JAVA_TOOL_OPTIONS="-Dspring.profiles.active=0 -Dserver.port=0 -Dspring.rsocket.server.port=0"
$ #./launcher
 <Exception Follows> 
``` 

# Deploy and bind to Consul

Here we will learn how and where service-discovery configurations propagate from code to container runtime.
If you're just starting and are learning to approach service-mesh development, this is a critical component
with a heap (pun intended) of academic and business theory churn happening pretty much until we develop 
"completely reliable" computing infrastructure.
 
Technologies such as [CRDT](https://www.infoq.com/presentations/crdt-production/) help solve the reliability problem by applying "failure-resistant service-mesh"
as a vital component to site operations. [Hashicorp's Consul](https://www.consul.io) does implement CRDT but also several key
registrar services. We will use Consul to register and discover our microservices in this case. 

## Consul dev server 

Visit and follow instructions [here](https://hub.docker.com/_/consul).

## Configure Spring

Using [AWK](https://www.unix.com/shell-programming-and-scripting/258882-exclude-first-line-when-awk.html), lets grab the IP of the server:

```shell script
docker exec -t dev-consul consul members |\
awk 'NR>1{ ADDR=$2; FS=":"; split(ADDR,a); print a[1],a[2]}'
```

NOTE: There's never a bad time to brush up on AWK.

The output will be used to fill in Spring parameters in our build script: 

```shell script
export CONSUL_HOST=<host-output>
export CONSUL_PORT=<port-output>
export JAVA_TOOL_OPTIONS=" ... -Dspring.cloud.consul.host=${CONSUL_HOST} -Dspring.cloud.consul.port=${CONSUL_PORT} "
...
```

# Build the app container

At this point, we have tucked all configuration details within a script, and that justifies 
our configuration detail for a single service. Furthermore, a practical requirement shifts parameters 
upstream into the build tool. We will do that at some point in the future - if it solves a problem.

A completed script template is next:

```shell script
export DOCKER_RUN=$1;shift
export CONSUL_HOST=172.17.0.2
export CONSUL_PORT=8500
export SERVER_PORT=6500
export SPRING_PROFILE="key-service"
export APP_PRIMARY="key"
export RSOCKET_PORT=6501
export APP_IMAGE_NAME="memory-key-service"
export APP_VERSION=0.0.1

export JAVA_TOOL_OPTIONS=" -Dspring.profiles.active=${SPRING_PROFILE}\
 -Dserver.port=${SERVER_PORT} -Dspring.rsocket.server.port=${RSOCKET_PORT}\
 -Dapp.primary=${APP_PRIMARY} -Dspring.cloud.consul.host=${CONSUL_HOST}\
 -Dspring.cloud.consul.port=${CONSUL_PORT} "
mvn spring-boot:build-image

[ ! -e $DOCKER_RUN ] && docker run --rm -d $APP_IMAGE_NAME:$APP_VERSION
```

This works for now.

## Links! 

[Spring Boot Maven Plugin](https://docs.spring.io/spring-boot/docs/current/maven-plugin/reference/html/)

[BuildPack JVM Options](https://paketo.io/docs/buildpacks/language-family-buildpacks/java/#runtime-jvm-configuration)

[Bellsoft Liberica Buildpack for JVM](https://github.com/paketo-buildpacks/bellsoft-liberica)

[Environment BuildPack](https://github.com/paketo-buildpacks/environment-variables)

[OCI Image Spec](https://github.com/opencontainers/image-spec/blob/master/config.md)

[Maven Git-Commit-id plugin](https://github.com/git-commit-id/git-commit-id-maven-plugin)

[Spring - Service Registration and Discovery](https://spring.io/guides/gs/service-registration-and-discovery/)

[GraalVM Spring-Boot](https://github.com/spring-projects-experimental/spring-graalvm-native)