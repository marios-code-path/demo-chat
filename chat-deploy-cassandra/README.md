# Deployment Objectives

We have several modules to deploy. Each module specifies one microservice.
Arguments and Environment commands shall be sent as deployment artifacts.

## How to specify which Main class?

In pom.xml...

## How to inject arguments into an execution? 

Start Key service in-memory:

```
 mvn spring-boot:run -Dspring-boot.run.profiles=memory-key -Dspring-boot.run.arguments="--app.server.port=5080 --app.rsocket.port=6400 --server.port=0 --app.primary=key"
```

### What we will do for arguments (later) 

Where do we put the arguments for execution, and how does this factor into multi artifact production?

## Configurations

### Build Config ( Buildpacks! )

## Entry Points

## Application Readiness Test - E2E tests
 
## Executions

## Links! 

[GraalVM Spring-Boot](https://github.com/spring-projects-experimental/spring-graalvm-native)

