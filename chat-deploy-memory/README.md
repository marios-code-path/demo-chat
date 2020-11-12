# Deployment Objectives

We have several modules to deploy. Each module specifies one microservice.
Arguments and Environment commands shall be sent with deployment artifacts.

These modules will ideally deploy without much fuss, and with uniform interaction from the operator.

## How to specify which Main class?

In pom.xml...

```xml
<build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <mainClass>com.demo.chat.deploy.app.memory.App</mainClass>
                </configuration>
            </plugin>
        </plugins>
</build>
```

Easy as that. Let's see how we can launch it with simplicity (lol).

## Launching from maven

We want to expect very specific runtime behaviour. Mainly
we need a default services port and profile set. The below snippet works when launching the 
jar from maven only:

POM.xml
```xml
 <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <jvmArguments>-Dspring.rsocket.server.port=6500 -Dserver.port=6501</jvmArguments>
                    <profiles>
                        <profile>key-service</profile>
                    </profiles>
                </configuration>
</plugin>
```
Thus, for the time being lets run from command-line. You can ignore this, as it's 
just illustration for getting to know runtime launch characteristics. 

Start Key service in-memory:

```shell script
mvn spring-boot:run -Dspring-boot.run.profiles="key-service"
```

This will keep memory key service running locally on its default ports.
There is no reason to limit this deployment to a single service. 

Launch persistence against our key-service:

```shell script
mvn spring-boot:run -Dspring-boot.run.profiles="persistence,key-client" -Dspring-boot.run.arguments="--server.port=7501 --spring.rsocket.server.port=7500"
```

Such deployment specifics need to get mixed into our build-time, so that the arguments can
be seen from inside the container launcher. The next section will discuss that.

### Launching services in Docker

Convey application properties like the command line variant of argument passing to our program (or JVM):

```shell script
java -jar my-jar.jar -Dspring.profiles.active=key-service -Dserver.port=6501 -Dspring.rsocket.server.port=6500
```

Lets see what happens when we attempt to launch our app in Docker, directly:

```shell script
docker run --rm chat-deploy-memory:0.0.1
```

Obviously, this won't work, since none of the default properties get sent.  So have a look at [the docs](https://paketo.io/docs/buildpacks/language-family-buildpacks/java/#runtime-jvm-configuration) to see
what we need to tell our container to pick up arguments externally. The docs say that `JAVA_TOOLS_OPTIONS` must be set 
for this to work, but let's find out the hard way.

Pop into an instance of the container with `sh` and mess around for a bit:

```shell script
docker run -it --entrypoint sh chat-deploy-memory:0.0.1
```

Behold a shell prompt within the cnb container. Neat!

```shell script
$ cd /cnb/lifecycle
$ ls
total 2200
-rwxr-xr-x 1 root root 2252800 Jan  1  1980 launcher
$ export JAVA_TOOL_OPTIONS="-Dspring.profiles.active=key-service -Dserver.port=6501 -Dspring.rsocket.server.port=6500"
$ ./launcher
 ........... 
```

This should succeed (or not, if consul isn't running! but thats OK!). 

``Every buildpack-generated image contains an executable called the launcher which can be used to execute a custom command in an environment containing buildpack-provided environment variables. The launcher will execute any buildpack provided profile scripts before running to provided command, in order to set environment variables with values that should be calculated dynamically at runtime.``

``To run a custom start command in the buildpack-provided environment set the ENTRYPOINT to launcher and provide the command using the container CMD.``

We're not interested in that last part, however knowing a launcher gets executed helps understand what is at hand
under the hood.  So all we really need to do is pass an environment variable to docker:

```shell script
export JAVA_TOOL_OPTIONS="-Dspring.profiles.active=key-service -Dserver.port=6501 -Dspring.rsocket.server.port=6500"
docker run --rm -e JAVA_TOOL_OPTIONS chat-deploy-memory:0.0.1
# alternately push env into a file, and specify with --env-file
```

Viola!  Without modifying BuildPack, we can simply take off from the command-line.  *HOWEVER*

### We're in it for the clouds

Our image Can startup without outside effort. According to [Environment BuildPack](https://github.com/paketo-buildpacks/environment-variables) docs,
we should be able to inject arguments into a build-pack specific environment (called `BPE_*`) that 
expand our `JAVA_TOOL_OPTIONS` into the command-line execution without fail. 

By inserting a `BPE_JAVA_TOOL_OPTIONS` env, and giving it the output of whatever is on our host's environment table.

pom.xml again:

```xml
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                        <env>
                            <BPE_JAVA_TOOL_OPTIONS>${env.JAVA_TOOL_OPTIONS}</BPE_JAVA_TOOL_OPTIONS>
                        </env>
                </configuration>
            </plugin>                    
```

For this, I have created a separate script just for kicking off the `memory-key-service` image:

```shell script
export JAVA_TOOL_OPTIONS="-Dspring.profiles.active=key-service -Dserver.port=6501 -Dspring.rsocket.server.port=6500"
mvn clean install spring-boot:build-image
```

## Links! 

[Spring Boot Maven Plugin](https://docs.spring.io/spring-boot/docs/current/maven-plugin/reference/html/)

[BuildPack JVM Options](https://paketo.io/docs/buildpacks/language-family-buildpacks/java/#runtime-jvm-configuration)

[Bellsoft Liberica Buildpack for JVM](https://github.com/paketo-buildpacks/bellsoft-liberica)

[Environment BuildPack](https://github.com/paketo-buildpacks/environment-variables)

[OCI Image Spec](https://github.com/opencontainers/image-spec/blob/master/config.md)

[Maven Git-Commit-id plugin](https://github.com/git-commit-id/git-commit-id-maven-plugin)

[GraalVM Spring-Boot](https://github.com/spring-projects-experimental/spring-graalvm-native)