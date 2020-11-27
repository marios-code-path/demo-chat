source ./deploy-ports.sh
mvn spring-boot:run -Dspring-boot.run.arguments=\
"--server.port=$((CORE_PORT+1)) --spring.rsocket.server.port=${CORE_PORT} \
--app.service.core.key \
--app.service.core.pubsub \
--app.service.core.index \
--app.service.core.persistence \
--app.primary=core"