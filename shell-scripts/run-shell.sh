#!/bin/bash

source ../shell-scripts/ports.sh

export APP_PRIMARY="shell"
export APP_IMAGE_NAME="chat-shell"
export MAIN_FLAGS="-Dspring.shell.interactive.enabled=true"
export CLIENT_FLAGS="-Dapp.client.protocol=rsocket \
-Dapp.client.rsocket.core.key \
-Dapp.client.rsocket.core.persistence -Dapp.client.rsocket.core.index -Dapp.client.rsocket.core.pubsub \
-Dapp.client.rsocket.core.secrets -Dapp.client.rsocket.composite.user -Dapp.client.rsocket.composite.message \
-Dapp.client.rsocket.composite.topic"
export SERVICE_FLAGS="-Dapp.service.core.key -Dapp.service.composite.auth"
export PORTS_FLAGS="-Dserver.port=9001 -Dmanagement.server.port=9001"
export OPT_FLAGS="-Dspring.autoconfigure.exclude=org.springframework.boot.autoconfigure.rsocket.RSocketServerAutoConfiguration"
export MANAGEMENT_ENDPOINTS="shutdown,health"
export ADDITIONAL_CONFIGS="classpath:/config/application-client.yml,"

function shell_local() {
  set -x
  ./build-app.sh -m chat-shell -k long -p shell -n ${APP_IMAGE_NAME} -b runlocal -d local -c /tmp/dc-keys $@
  exit 0
}

function shell_docker() {
  set -x
  export KEY_VOLUME="demo-chat-server-keys"
  export DOCKER_ARGS="-it -v ${KEY_VOLUME}:/etc/keys"

  ./build-app.sh -m chat-shell -k long -p shell,client -n ${APP_IMAGE_NAME} -d consul -b rundocker -c /etc/keys $@
  exit 0
}


RUN_CMD=$1; shift

if declare -F "$RUN_CMD" > /dev/null; then
  $RUN_CMD $@
else
  echo "Unknown command: $RUN_CMD"
  exit 1
fi