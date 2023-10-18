JAVA_TOOL_OPTIONS=   -Dspring.profiles.active=prod -Dmanagement.endpoint.shutdown.enabled=true -Dmanagement.endpoint.health.enabled=true
-Dmanagement.endpoint.rootkeys.enabled=true  -Dmanagement.endpoints.web.exposure.include=shutdown,health,rootkeys -Dapp.key.type=long
-Dapp.primary=core-service -Dspring.application.name=core-service-rsocket -Djava.security.egd=file:/dev/./urandom
-Dspring.rsocket.server.ssl.enabled=false -Dserver.port=6791 -Dmanagement.server.port=6791 -Dspring.rsocket.server.port=6790
-Dapp.users.create=true -Dapp.rootkeys.create=true -Dspring.cloud.consul.enabled=false -Dspring.cloud.service-registry.auto-registration.enabled=false
-Dspring.cloud.consul.config.enabled=false -Dspring.cloud.consul.config.watch.enabled=false -Dspring.cloud.consul.discovery.enabled=false
-Dspring.security.user.name=actuator -Dspring.security.user.password=actuator -Dspring.security.user.roles=ACTUATOR
  -Dspring.main.web-application-type=reactive -Dapp.server.proto=http -Dapp.service.core.key -Dapp.service.core.pubsub -Dapp.service.core.index
  -Dapp.service.core.persistence -Dapp.service.core.secrets -Dapp.service.composite -Dapp.service.composite.auth -Dapp.controller.persistence
  -Dapp.controller.index -Dapp.controller.key -Dapp.client.discovery=local -Dapp.controller.pubsub -Dapp.controller.secrets -Dapp.controller.user
  -Dapp.controller.topic -Dapp.controller.message -Dspring.rsocket.server.transport=websocket -Dspring.rsocket.server.mapping-path=/
  -Dapp.service.security.userdetails -Dspring.config.additional-location=classpath:/config/logging.yml,classpath:/config/management-defaults.yml,classpath:/config/userinit.yml -Dspring-boot.run.arguments=
