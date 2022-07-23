source ../shell-scripts/ports.sh
source ../shell-scripts/astra-options.sh
export DOCKER_RUN=$1;shift
# how to auto-discover consul using dns alone!
export SPRING_PROFILE="cassandra-astra"
export APP_PRIMARY="core"
export APP_IMAGE_NAME="core-service-cassandra"
export APP_VERSION=0.0.1

# makes no use of cloud configuration or config-maps
# TODO: difference between '-D' and '--'
export JAVA_TOOL_OPTIONS=" -Dspring.profiles.active=${SPRING_PROFILE} \
-Dserver.port=$((CORE_PORT+1)) -Dspring.rsocket.server.port=${CORE_PORT} \
-Dapp.service.core.key=${KEYSPACE_TYPE} -Dapp.service.core.pubsub -Dapp.service.core.index \
-Dapp.service.core.persistence -Dapp.service.edge.topic \
-Dapp.service.edge.user -Dapp.service.edge.messaging \
-Dapp.primary=core -Dspring.cloud.consul.host=${CONSUL_HOST} \
-Dspring.cloud.consul.port=${CONSUL_PORT} \
-Dspring.shell.interactive.enabled=false \
${CASSANDRA_OPTIONS}"

cd ../chat-deploy-cassandra

[[ $DOCKER_RUN == "" ]] && exit
[[ $DOCKER_RUN == "build" ]] && MAVEN_ARG="spring-boot:build-image"
[[ $DOCKER_RUN == "run" ]] && MAVEN_ARG="spring-boot:run"

mvn -DimageName=${APP_IMAGE_NAME} -DmainClass=${APP_MAIN_CLASS} $MAVEN_ARG

[[ $DOCKER_RUN == "docker" ]] && docker run --rm -d $APP_IMAGE_NAME:$APP_VERSION
