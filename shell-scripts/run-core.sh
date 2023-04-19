#!/bin/bash
source ../shell-scripts/ports.sh

set -e


export KEY_VOLUME="demo-chat-server-keys"
export DOCKER_ARGS="-it -v ${KEY_VOLUME}:/etc/keys --expose ${RSOCKET_PORT} -p ${RSOCKET_PORT}:${RSOCKET_PORT}/tcp \
--expose ${HTTP_PORT} -p ${HTTP_PORT}:${HTTP_PORT}/tcp"

export PORTS_FLAGS="-Dserver.port=${HTTP_PORT} -Dmanagement.server.port=${HTTP_PORT} -Dspring.rsocket.server.port=${RSOCKET_PORT}"
export SERVICE_FLAGS="-Dapp.server.proto=rsocket -Dapp.service.core.key -Dapp.service.core.pubsub -Dapp.service.core.index \
-Dapp.service.core.persistence -Dapp.service.core.secrets -Dapp.service.composite -Dapp.service.composite.auth \
-Dapp.controller.persistence -Dapp.controller.index -Dapp.controller.key \
-Dapp.controller.pubsub -Dapp.controller.secrets -Dapp.controller.user -Dapp.controller.topic -Dapp.controller.message"
export BUILD_PROFILES="consul,"
export DISCOVERY_FLAGS="-Dspring.config.import=optional:consul:"

function memory() {
  export APP_PRIMARY="core-service"
  export APP_IMAGE_NAME="memory-${APP_PRIMARY}-rsocket"

  ./build-app.sh -m chat-deploy-memory -p prod,consul -n ${APP_IMAGE_NAME} -k long -d consul \
-b rundocker -c file:/etc/keys -i users,rootkeys $@
}

function cassandra() {
    source ../shell-scripts/cassandra-options.sh

    export APP_PRIMARY="core-service"
    export APP_IMAGE_NAME="cassandra-${APP_PRIMARY}-rsocket"
    BUILD_PROFILES+="cassandra,"

    ./build-app.sh -m chat-deploy-cassandra ${APP_IMAGE_NAME} -p prod,consul -n ${APP_IMAGE_NAME} -k long -d consul \
-b rundocker -c file:/etc/keys -i users,rootkeys $@
}

function cassandra_astra() {
    #../astra/rw-token.json ../astra/secure-connect-demochat.zip
    source ../shell-scripts/astra-options.sh

    export APP_PRIMARY="core-service"
    export APP_IMAGE_NAME="astra-${APP_PRIMARY}-rsocket"
    BUILD_PROFILES+="cassandra-astra,"

    ./build-app.sh -m chat-deploy-cassandra ${APP_IMAGE_NAME} -p prod,consul -n ${APP_IMAGE_NAME} -k long -d consul \
-b rundocker -c file:/etc/keys -i users,rootkeys $@
}

RUN_CMD=$1; shift

if declare -F "$RUN_CMD" > /dev/null; then
  $RUN_CMD $@
else
  echo "Unknown command: $RUN_CMD"
  exit 1
fi