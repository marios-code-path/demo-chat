# (WIP) Configuring Service Discovery with Consul

This module isolates Service Discovery and Key/Value storage as a dependency
when Consul is wanted in the environment.Since this app abstracts the Key/Value stores and Service discovery, we
can implement them with Reactive Consul via Spring.  


## Turning this module on

This module adds consule KV and Service Discovery via Consul, and as such it adds
additional options for locating Consul with the following properties:

```
spring.cloud.consul.host=...
spring.cloud.consul.port=...
app.kv.store.prefix=...
app.init.type=...
```

### Service mode

When the app is built for Consul in mind, simply add the following dependency:

```
<dependency>
	<groupId>com.demo.chat</groupId>
    <artifactId>chat-client-consul</artifactId>
</dependency>
```

This will bring in Spring/Consul Reactive components and enables the initialization
of app states via Consul KV. Consequently, this also allows for service registration
via the ```spring.cloud.discovery...``` flag.

To engage app-state KV initialization, set the following flags

```
<app-state KV properties>
```

In order to activate service registration, set these properties:

```
<service-registartion properties>
```

### Client mode

Clients will want to use ClientDiscovery to find Services registered in Consul.
This means, users will have the option to specify discovery via Properties AND consul,
although this is not required.

to enable discovery via Consul, set the following properties

```
<PROPERTIS USED TO ENABLE CONSULE SERVICE DISCOVERY>
```

## Testing

For whatever reason, it is not recommended to test that a serivce is registred under consul.
You may need to run environment-integration tests for that to happen.

In other words, given a CI pipeline, one may deploy the app, along with environment
services (in this case Consul), then observe registration state within consul,
and possibly observe actuator for particular objects.


## Data recovery
