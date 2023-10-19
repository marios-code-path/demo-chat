#!/bin/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

source $DIR/util.sh
source $DIR/ports.sh

export APP_PRIMARY="gateway"
export APP_IMAGE_NAME="chat-gateway"
# Authorization server needs to access user accounts and secrets
export PORTS_FLAGS="-Dserver.port=${GATEWAY_HTTP_PORT} -Dmanagement.server.port=${GATEWAY_HTTP_MGMT_PORT}"
export OPT_FLAGS="-Dkeycert=file:${JWK_KEYPATH}/server_keystore.p12 \
 -Dapp.oauth2.jwk.path=file:${JWK_KEYPATH}/server_keycert.jwk -Dapp.rest.port=6791"
export MANAGEMENT_ENDPOINTS="shutdown,health"
export ADDITIONAL_CONFIGS=""
export JWK_KEYPATH="${JWK_KEYPATH:-/tmp/dc-keys}"
export CLIENT_FLAGS="-Dapp.client.protocol=rsocket"

export KEYTYPE=long

function local() {

  $DIR/build-app.sh -m chat-deploy -d local -b runlocal -k ${KEYTYPE} -n ${APP_IMAGE_NAME} -e gateway -s client $@
}

function docker() {
  OPT_FLAGS+=" -Dkeycert=file:/etc/keys/server_keystore.p12 -Dapp.oauth2.jwk.path=file:/etc/keys/server_keycert.jwk"

  export DOCKER_ARGS="--expose 80 -p 80:80/tcp \
--expose ${CORE_MGMT_PORT} -p ${CORE_MGMT_PORT}:${CORE_MGMT_PORT}/tcp"

 $DIR/build-app.sh -m chat-deploy -k ${KEYTYPE} -n ${APP_IMAGE_NAME} -d consul -e gateway -s client -b rundocker -c /etc/keys $@
}

function docker_image() {
    OPT_FLAGS+=" -Dkeycert=file:${JWK_KEYPATH}/server_keystore.p12 -Dapp.oauth2.jwk.path=file:${JWK_KEYPATH}/server_keycert.jwk"

  $DIR/build-app.sh -m chat-deploy -k ${KEYTYPE} -n ${APP_IMAGE_NAME} -d consul -b build -c /tmp/dc-keys $@
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