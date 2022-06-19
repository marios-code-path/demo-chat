source ../shell-scripts/ports.sh
export DOCKER_RUN=$1;shift
# how to auto-discover consul using dns alone!
export SPRING_PROFILE="default"
export APP_PRIMARY="edge"
export APP_IMAGE_NAME="edge-services-monolith"
export APP_VERSION=0.0.1

# makes no use of cloud configuration or config-maps
export JAVA_TOOL_OPTIONS=" -Dspring.profiles.active=${SPRING_PROFILE} \
-Dserver.port=$((EDGE_PORT+1)) -Dspring.rsocket.server.port=${EDGE_PORT} \
-Dapp.service.edge.topic -Dapp.service.edge.user -Dapp.service.edge.messaging \
-Dapp.primary=edge -Dspring.cloud.consul.host=${CONSUL_HOST} \
-Dspring.cloud.consul.port=${CONSUL_PORT} "

mvn spring-boot:build-image

[ ! -e $DOCKER_RUN ] && docker run --rm -d $APP_IMAGE_NAME:$APP_VERSION