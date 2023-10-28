#!/bin/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

source $DIR/util.sh
source $DIR/ports.sh

export APP_PRIMARY="REST"
export APP_IMAGE_NAME="chat-rest"
# Authorization server needs to access user accounts and secrets
export CLIENT_FLAGS="-Dapp.client.protocol=rsocket -Dapp.server.proto=http \
-Dapp.client.rsocket.composite.user -Dapp.client.rsocket.composite.topic -Dapp.client.rsocket.composite.message \
-Dapp.client.rsocket.composite.message -Dapp.controller.user -Dapp.controller.topic -Dapp.controller.message"
export PORTS_FLAGS="-Dserver.port=${HTTP_PORT} -Dmanagement.server.port=${HTTP_MGMT_PORT}"
export OPT_FLAGS="-Dspring.autoconfigure.exclude=org.springframework.boot.autoconfigure.rsocket.RSocketServerAutoConfiguration \
 -Dlogging.level.com.demo.chat.client.rsocket=DEBUG"
export MANAGEMENT_ENDPOINTS="shutdown,health"
export JWK_KEYPATH="${JWK_KEYPATH:-/tmp/keys}"

export KEYTYPE=long

function local() {
    OPT_FLAGS+=" -Dkeycert=file:${JWK_KEYPATH}/server_keystore.p12 -Dapp.oauth2.jwk.path=file:${JWK_KEYPATH}/server_keycert.jwk"

  $DIR/build-app.sh -m chat-deploy -k ${KEYTYPE} -n ${APP_IMAGE_NAME} -d local -b runlocal -c ${JWK_KEYPATH} $@
}

function docker() {
  OPT_FLAGS+=" -Dkeycert=file:/etc/keys/server_keystore.p12 -Dapp.oauth2.jwk.path=file:/etc/keys/server_keycert.jwk"

  export DOCKER_ARGS="--expose ${HTTP_PORT} -p ${HTTP_PORT}:${HTTP_PORT}/tcp \
--expose ${HTTP_MGMT_PORT} -p ${HTTP_MGMT_PORT}:${HTTP_MGMT_PORT}/tcp"

 $DIR/build-app.sh -m chat-deploy -k long -n ${APP_IMAGE_NAME} -d consul -b rundocker -c /etc/keys $@
}

function docker_image() {
    OPT_FLAGS+=" -Dkeycert=file:${JWK_KEYPATH}/server_keystore.p12 -Dapp.oauth2.jwk.path=file:${JWK_KEYPATH}/server_keycert.jwk"

  $DIR/build-app.sh -m chat-deploy -k long -n ${APP_IMAGE_NAME} -d consul -b build -c /etc/keys $@
}


function help_message() {
  cat << EOF
Usage: $0 [deployment-profile] [options]
Available deployment-profiles:
  local
  docker
  docker_image
EOF
}

# -- main() --
std_exec $@