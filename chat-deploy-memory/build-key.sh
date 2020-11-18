export DOCKER_RUN=$1;shift
export CONSUL_HOST=172.17.0.2
export CONSUL_PORT=8500
export SERVER_PORT=6500
export SPRING_PROFILE="key"
export APP_PRIMARY="key"
export RSOCKET_PORT=6501
export APP_IMAGE_NAME="memory-key-service"
export APP_VERSION=0.0.1

export JAVA_TOOL_OPTIONS=" -Dspring.profiles.active=${SPRING_PROFILE} -Dserver.port=${SERVER_PORT} -Dspring.rsocket.server.port=${RSOCKET_PORT} -Dapp.primary=${APP_PRIMARY} -Dspring.cloud.consul.host=${CONSUL_HOST} -Dspring.cloud.consul.port=${CONSUL_PORT} "
mvn spring-boot:build-image

[ ! -e $DOCKER_RUN ] && docker run --rm -d $APP_IMAGE_NAME:$APP_VERSION