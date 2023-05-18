#!/bin/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"


source $DIR/util.sh

export APP_PRIMARY="shell"
export APP_IMAGE_NAME="chat-shell"
export MAIN_FLAGS="-Dspring.shell.interactive.enabled=true -Dspring.main.web-application-type=reactive"
export CLIENT_FLAGS="-Dapp.client.protocol=rsocket \
-Dapp.client.rsocket.core.key \
-Dapp.client.rsocket.core.persistence -Dapp.client.rsocket.core.index -Dapp.client.rsocket.core.pubsub \
-Dapp.client.rsocket.core.secrets -Dapp.client.rsocket.composite.user -Dapp.client.rsocket.composite.message \
-Dapp.client.rsocket.composite.topic"
export SERVICE_FLAGS="-Dapp.service.core.key -Dapp.service.composite.auth"
export OPT_FLAGS="-Dspring.autoconfigure.exclude=org.springframework.boot.autoconfigure.rsocket.RSocketServerAutoConfiguration"

if [[ ! -z ${DEBUG} ]]; then
OPT_FLAGS+="-Dlogging.level.com.demo.chat.client.rsocket=debug \
-Dlogging.level.reactor.netty.http.client=DEBUG"
fi

function local() {
  set -e
  $DIR/build-app.sh -m chat-shell -k long -p shell -n ${APP_IMAGE_NAME} -b runlocal -d local $@
  exit 0
}

function docker() {
  set -e
  export DOCKER_ARGS="-it"
  export PORTS_FLAGS="-Dserver.port=9001 -Dmanagement.server.port=9001"
  export MANAGEMENT_ENDPOINTS="shutdown,health"

  $DIR/build-app.sh -m chat-shell -k long -p shell,client -n ${APP_IMAGE_NAME} -d consul -b rundocker -c /etc/keys $@
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