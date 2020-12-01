source ./deploy-ports.sh
mvn spring-boot:run -Dspring-boot.run.arguments=\
"--server.port=$((CORE_PORT+1)) --spring.rsocket.server.port=${CORE_PORT} \
--app.service.core.key \
--app.service.core.pubsub \
--app.service.core.index \
--app.service.core.persistence \
--app.service.edge.topic \
--app.service.edge.user \
--app.service.edge.messaging \
--app.primary=core"