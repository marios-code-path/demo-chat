export DOCKER_RUN=$1;shift
# how to auto-discover consul using dns alone!
export CONSUL_HOST=172.17.0.2
export CONSUL_PORT=8500
export SPRING_PROFILE="default"
export APP_PRIMARY="core"
export APP_IMAGE_NAME="core-services-monolith"
export APP_VERSION=0.0.1

export CORE_PORT=6790

# makes no use of cloud configuration or config-maps
# TODO: difference between '-D' and '--'
export JAVA_TOOL_OPTIONS=" -Dspring.profiles.active=${SPRING_PROFILE} \
-Dserver.port=$((CORE_PORT+1)) -Dspring.rsocket.server.port=${CORE_PORT} \
-Dapp.service.core.key -Dapp.service.core.pubsub -Dapp.service.core.index \
-Dapp.service.core.persistence -Dapp.service.edge.topic \
-Dapp.service.edge.user -Dapp.service.edge.messaging \
-Dapp.primary=core -Dspring.cloud.consul.host=${CONSUL_HOST} \
-Dspring.cloud.consul.port=${CONSUL_PORT} "

mvn spring-boot:build-image

[ ! -e $DOCKER_RUN ] && docker run --rm -d $APP_IMAGE_NAME:$APP_VERSION
