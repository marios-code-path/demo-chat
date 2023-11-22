#!/bin/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

source $DIR/util.sh
source $DIR/ports.sh

export CLIENT_PROTO="rsocket"
export APP_PRIMARY="authserv"
export APP_IMAGE_NAME="chat-authserv"
# Authorization server needs to access user accounts and secrets
export CLIENT_FLAGS="-Dapp.client.protocol=rsocket \
-Dapp.client.rsocket.core.key \
-Dapp.client.rsocket.core.persistence -Dapp.client.rsocket.core.index -Dapp.oauth2.entrypoint-path=localhost:9090 \
-Dapp.client.rsocket.core.secrets -Dapp.client.rsocket.composite.user \
-Dapp.service.composite.auth"
export PORTS_FLAGS="-Dserver.port=${AUTHSERV_HTTP_PORT} -Dmanagement.server.port=${AUTHSERV_MGMT_PORT}"
export OPT_FLAGS="-Dspring.autoconfigure.exclude=org.springframework.boot.autoconfigure.rsocket.RSocketServerAutoConfiguration \
-Dspring.main.web-application-type=servlet -Dlogging.level.com.demo.chat.client.rsocket=DEBUG"
export MANAGEMENT_ENDPOINTS="shutdown,health"
export ADDITIONAL_CONFIGS="classpath:/config/server-authserv-consul.yml,classpath:/config/oauth2-client.yml,"
export JWK_KEYPATH="${JWK_KEYPATH:-/etc/keys}"

export KEY_TYPE=long

function local() {
    OPT_FLAGS+=" -Dkeycert=file:${JWK_KEYPATH}/server_keystore.p12 -Dapp.oauth2.jwk.path=file:${JWK_KEYPATH}/server_keycert.jwk"
    export BUILD_PROFILES="jdbc,"
    export SPRING_RUN_ARGUMENTS="--clientpath='classpath:client.json'"
    OPT_FLAGS+=" -DSpring.datasource.url=jdbc:postgresql://postgres:5432/authserver -Dspring.datasource.username=user -Dspring.datasource.password=password"

  $DIR/build-app.sh -m chat-authorization-server -k ${KEY_TYPE} -s client -n ${APP_IMAGE_NAME} -d local -b runlocal -c ${JWK_KEYPATH} $@
}

function docker_image() {
    OPT_FLAGS+=" -Dkeycert=file:${JWK_KEYPATH}/server_keystore.p12 -Dapp.oauth2.jwk.path=file:${JWK_KEYPATH}/server_keycert.jwk"

  $DIR/build-app.sh -m chat-authorization-server -s client -k ${KEY_TYPE} -n ${APP_IMAGE_NAME} -d consul -b build -c /tmp/dc-keys $@
}


function help_message() {
  cat << EOF
Usage: $0 [deployment-profile] [options]
Available deployment-profiles:
  local
  docker
  docker_image

Available Environment:
JWK_KEYPATH = /tmp/keys
EOF
}

# -- main() --
std_exec $@