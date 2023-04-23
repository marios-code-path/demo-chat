#!/bin/bash

source ../shell-scripts/util.sh
source ../shell-scripts/ports.sh

export DOCKER_ARGS=" --expose ${CORE_RSOCKET_PORT} -p ${CORE_RSOCKET_PORT}:${CORE_RSOCKET_PORT}/tcp \
--expose ${CORE_MGMT_PORT} -p ${CORE_MGMT_PORT}:${CORE_MGMT_PORT}/tcp"
export PORTS_FLAGS="-Dserver.port=${CORE_MGMT_PORT} -Dmanagement.server.port=${CORE_MGMT_PORT} \
-Dspring.rsocket.server.port=${CORE_RSOCKET_PORT}"
export SERVICE_FLAGS="-Dapp.server.proto=rsocket -Dapp.service.core.key -Dapp.service.core.pubsub -Dapp.service.core.index \
-Dapp.service.core.persistence -Dapp.service.core.secrets -Dapp.service.composite -Dapp.service.composite.auth \
-Dapp.controller.persistence -Dapp.controller.index -Dapp.controller.key \
-Dapp.controller.pubsub -Dapp.controller.secrets -Dapp.controller.user -Dapp.controller.topic -Dapp.controller.message"
export BUILD_PROFILES="consul,"
export DISCOVERY_FLAGS="-Dspring.config.import=optional:consul:"
export ADDITIONAL_CONFIGS="classpath:/config/server-rsocket-consul.yml,"
export MANAGEMENT_ENDPOINTS="shutdown,health,rootkeys"

function memory() {
  DOCKER_ARGS+=" -it -d"
  export APP_PRIMARY="core-service"
  export APP_IMAGE_NAME="memory-${APP_PRIMARY}-rsocket"

  ./build-app.sh -m chat-deploy-memory -p prod,consul -n core-service-rsocket -k long -d consul \
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

function help_message {
cat << EOF
Usage: $0 [deployment-profile] [options]
Available deployment-profiles:
  memory
  cassandra
  cassandra_astra
EOF
exit 1
}
# ---- main() ----

std_exec $@