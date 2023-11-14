#!/bin/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

source $DIR/util.sh
source $DIR/ports.sh

export APP_PRIMARY="REST"
export APP_IMAGE_NAME="chat-rest"
export APP_SERVER_PROTO="http"
# Authorization server needs to access user accounts and secrets
export CLIENT_FLAGS="-Dapp.client.protocol=rsocket -Dapp.server.proto=${APP_SERVER_PROTO} \
-Dapp.client.rsocket.composite.user -Dapp.client.rsocket.composite.topic -Dapp.client.rsocket.composite.message \
-Dapp.client.rsocket.composite.message -Dapp.controller.user -Dapp.controller.topic -Dapp.controller.message"
export PORTS_FLAGS="-Dserver.port=${HTTP_PORT} -Dmanagement.server.port=${HTTP_MGMT_PORT}"
export OPT_FLAGS="-Dspring.autoconfigure.exclude=org.springframework.boot.autoconfigure.rsocket.RSocketServerAutoConfiguration \
 -Dlogging.level.com.demo.chat.client.rsocket=DEBUG"
export MANAGEMENT_ENDPOINTS="shutdown,health"
export JWK_KEYPATH="${JWK_KEYPATH:-/tmp/keys}"

export KEY_TYPE=long

function local() {
    OPT_FLAGS+=" -Dkeycert=file:${JWK_KEYPATH}/server_keystore.p12 -Dapp.oauth2.jwk.path=file:${JWK_KEYPATH}/server_keycert.jwk"

  $DIR/build-app.sh -m chat-deploy -k ${KEY_TYPE} -n core-service-http -b runlocal -c ${JWK_KEYPATH} "$@"
}

function docker_image() {
    OPT_FLAGS+=" -Dkeycert=file:${JWK_KEYPATH}/server_keystore.p12 -Dapp.oauth2.jwk.path=file:${JWK_KEYPATH}/server_keycert.jwk"

  $DIR/build-app.sh -m chat-deploy -k ${KEY_TYPE} -n core-service-http -b build -c /etc/keys "$@"
}

function help_message() {
  cat << EOF
Usage: $0 [deployment-profile] [options]
Available deployment-profiles:
  local
  docker_image
EOF
}

# -- main() --
std_exec "$@"