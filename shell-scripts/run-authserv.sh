#!/bin/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

source $DIR/util.sh
source $DIR/ports.sh

export APP_PRIMARY="authserv"
export APP_IMAGE_NAME="chat-authserv"
# Authorization server needs to access user accounts and secrets
export CLIENT_FLAGS="-Dapp.client.protocol=rsocket \
-Dapp.client.rsocket.core.key \
-Dapp.client.rsocket.core.persistence -Dapp.client.rsocket.core.index \
-Dapp.client.rsocket.core.secrets -Dapp.client.rsocket.composite.user \
-Dapp.service.security.userdetails -Dapp.service.composite.auth"
export PORTS_FLAGS="-Dserver.port=${AUTHSERV_HTTP_PORT} -Dmanagement.server.port=${AUTHSERV_MGMT_PORT}"
export OPT_FLAGS="-Dspring.autoconfigure.exclude=org.springframework.boot.autoconfigure.rsocket.RSocketServerAutoConfiguration \
-Dspring.main.web-application-type=servlet"
export MANAGEMENT_ENDPOINTS="shutdown,health"
export ADDITIONAL_CONFIGS="classpath:/config/server-authserv-consul.yml,classpath:/config/oauth2-client.yml,"
export JWK_KEYPATH="${JWK_KEYPATH:-/tmp/dc-keys}"

function local() {
    OPT_FLAGS+=" -Dkeycert=file:${JWK_KEYPATH}/server_keystore.p12 -Dapp.oauth2.jwk.path=file:${JWK_KEYPATH}/server_keycert.jwk"

  $DIR/build-app.sh -m chat-authorization-server -k long -n ${APP_IMAGE_NAME} -d local -b runlocal -c /tmp/dc-keys $@
}

function docker() {
  OPT_FLAGS+=" -Dkeycert=file:/etc/keys/server_keystore.p12 -Dapp.oauth2.jwk.path=file:/etc/keys/server_keycert.jwk"

  export DOCKER_ARGS="--expose ${AUTHSERV_HTTP_PORT} -p ${AUTHSERV_HTTP_PORT}:${AUTHSERV_HTTP_PORT}/tcp \
--expose ${AUTHSERV_MGMT_PORT} -p ${AUTHSERV_MGMT_PORT}:${AUTHSERV_MGMT_PORT}/tcp"

 $DIR/build-app.sh -m chat-authorization-server -k long -n ${APP_IMAGE_NAME} -d consul -b rundocker -c /etc/keys $@
}

function docker_image() {
    OPT_FLAGS+=" -Dkeycert=file:${JWK_KEYPATH}/server_keystore.p12 -Dapp.oauth2.jwk.path=file:${JWK_KEYPATH}/server_keycert.jwk"

  $DIR/build-app.sh -m chat-authorization-server -k long -n ${APP_IMAGE_NAME} -d consul -b build -c /tmp/dc-keys $@
}


function help_message() {
  cat << EOF
Usage: $0 [deployment-profile] [options]
Available deployment-profiles:
  authserv_local
  authserv_docker
EOF
}

# -- main() --
std_exec $@