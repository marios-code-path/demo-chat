source ./deploy-ports.sh
mvn spring-boot:run -Dspring-boot.run.arguments=\
"--server.port=$((EDGE_PORT+1)) --spring.rsocket.server.port=${EDGE_PORT} \
--app.client.rsocket.core.key \
--app.client.rsocket.core.pubsub \
--app.client.rsocket.core.index \
--app.client.rsocket.core.persistence \
--app.service.edge.topic \
--app.service.edge.user \
--app.service.edge.messaging \
--app.primary=edge"
