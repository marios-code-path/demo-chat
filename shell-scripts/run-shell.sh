#!/bin/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

source $DIR/ports.sh
source $DIR/util.sh

export CLIENT_PROTO="rsocket"
export APP_PRIMARY="shell"
export APP_IMAGE_NAME="chat-shell"
export MAIN_FLAGS="-Dspring.shell.interactive.enabled=true -Dspring.main.web-application-type=reactive"
export CLIENT_FLAGS="-Dapp.client.protocol=rsocket \
-Dspring.autoconfigure.exclude=org.springframework.boot.autoconfigure.rsocket.RSocketServerAutoConfiguration \
-Dapp.client.rsocket.core.key \
-Dapp.client.rsocket.core.persistence -Dapp.client.rsocket.core.index -Dapp.client.rsocket.core.pubsub \
-Dapp.client.rsocket.core.secrets -Dapp.client.rsocket.composite.user -Dapp.client.rsocket.composite.message \
-Dapp.client.rsocket.composite.topic"
export SERVICE_FLAGS="-Dapp.service.core.key -Dapp.service.composite.auth"
OPT_FLAGS+=" -Dlogging.level.io.rsocket.FrameLogger=OFF -Dspring.autoconfigure.exclude=org.springframework.boot.autoconfigure.rsocket.RSocketServerAutoConfiguration"

function local() {
  set -e
  export PORTS_FLAGS="-Dserver.port=${SHELL_MGMT_PORT} -Dmanagement.server.port=${SHELL_MGMT_PORT}"
  export MANAGEMENT_ENDPOINTS="shutdown,health,rootkeys"

  $DIR/build-app.sh -m chat-deploy -k long -p shell -e shell -s client -n ${APP_IMAGE_NAME} -b runlocal -d local $@
  exit 0
}

function help_message() {
  cat << EOF
Usage: $0 [deployment-profile] [options]
Available deployment-profiles:
 docker
 local
EOF
}
# ---- main() ----

std_exec $@