#!/bin/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

export EXEC=$1; shift

function help_message {
cat << EOF
Usage: $0 [execution-strategy] [deployment-profile] [options]
Available execution-strategy:
    build
    rundocker
    runlocal

Available deployment-profiles:
  memory
  memory_local
  cassandra
  cassandra_astra
EOF
exit 1
}

if [[ -z $EXEC ]]; then
    help_message
    exit 1
fi

source $DIR/util.sh
source $DIR/ports.sh

export DOCKER_ARGS=" --expose ${CORE_RSOCKET_PORT} -p ${CORE_RSOCKET_PORT}:${CORE_RSOCKET_PORT}/tcp \
--expose ${CORE_MGMT_PORT} -p ${CORE_MGMT_PORT}:${CORE_MGMT_PORT}/tcp"
export PORTS_FLAGS="-Dserver.port=${CORE_MGMT_PORT} -Dmanagement.server.port=${CORE_MGMT_PORT} \
-Dspring.rsocket.server.port=${CORE_RSOCKET_PORT}"
export SERVICE_FLAGS="-Dspring.main.web-application-type=reactive -Dapp.server.proto=rsocket -Dapp.service.core.key -Dapp.service.core.pubsub -Dapp.service.core.index \
-Dapp.service.core.persistence -Dapp.service.core.secrets -Dapp.service.composite -Dapp.service.composite.auth \
-Dapp.controller.persistence -Dapp.controller.index -Dapp.controller.key \
-Dapp.controller.pubsub -Dapp.controller.secrets -Dapp.controller.user -Dapp.controller.topic -Dapp.controller.message"
export BUILD_PROFILES="consul,"
export DISCOVERY_FLAGS="-Dspring.config.import=optional:consul:"
export ADDITIONAL_CONFIGS="classpath:/config/server-rsocket-consul.yml,"
export MANAGEMENT_ENDPOINTS="shutdown,health,rootkeys"
OPT_FLAGS+=" -Dlogging.level.io.rsocket.FrameLogger=OFF"

function memory_local() {
    export DOCKER_ARGS=
    export BUILD_PROFILES=
    export DISCOVERY_FLAGS=
    export ADDITIONAL_CONFIGS=

    export APP_PRIMARY="core-service"
    export APP_IMAGE_NAME="memory-${APP_PRIMARY}-rsocket"

   $DIR/build-app.sh -m chat-deploy-memory -p prod -n core-service-rsocket -k long \
-d local -b ${EXEC} -c ${CERT_DIR} -i users,rootkeys $@
}

function memory() {
  export CERT_DIR=${CERT_DIR:=/etc/keys}
  DOCKER_ARGS+=" -it -d"
  export APP_PRIMARY="core-service"
  export APP_IMAGE_NAME="memory-${APP_PRIMARY}-rsocket"

  $DIR/build-app.sh -m chat-deploy-memory -p prod -n core-service-rsocket -k long  \
-b ${EXEC} -c ${CERT_DIR} -i users,rootkeys $@

  # $DIR/build-app.sh -m chat-deploy-memory -p prod,consul -n core-service-rsocket -k long -d consul \
#-b ${EXEC} -c ${CERT_DIR} -i users,rootkeys $@
}

function cassandra() {
    source $DIR/cassandra-options.sh

    export DOCKER_ARGS=
    export BUILD_PROFILES=
    export DISCOVERY_FLAGS=
    export ADDITIONAL_CONFIGS=

    export APP_PRIMARY="core-service"
    export APP_IMAGE_NAME="cassandra-${APP_PRIMARY}-rsocket"
    BUILD_PROFILES+="cassandra,"

    cassandra_options localhost chat_long demochat

    export OPT_FLAGS="${CASSANDRA_OPTIONS}"

    $DIR/build-app.sh -m chat-deploy-cassandra -p cassandra-contact-point -n core-service-rsocket -k long \
-b ${EXEC} -i users,rootkeys $@

#    $DIR/build-app.sh -m chat-deploy-cassandra ${APP_IMAGE_NAME} -p prod,consul -n ${APP_IMAGE_NAME} -k long -d consul \
#-b ${EXEC} -c ${CERT_DIR} -i users,rootkeys $@
}

function cassandra_astra() {
    #../astra/rw-token.json ../astra/secure-connect-demochat.zip
    source $DIR/astra-options.sh

    export APP_PRIMARY="core-service"
    export APP_IMAGE_NAME="astra-${APP_PRIMARY}-rsocket"
    BUILD_PROFILES+="cassandra-astra,"

    $DIR/build-app.sh -m chat-deploy-cassandra ${APP_IMAGE_NAME} -p prod,consul -n ${APP_IMAGE_NAME} -k long -d consul \
-b ${EXEC} -i users,rootkeys $@
}


# ---- main() ----

std_exec $@