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

export KEYTYPE=long

function local() {

  $DIR/build-app.sh -m chat-deploy -d local -b runlocal -k ${KEYTYPE} -n ${APP_IMAGE_NAME} -e gateway "$@"
}


function help_message() {
  cat << EOF
Usage: $0 [deployment-profile] [options]
Available deployment-profiles:
  local
EOF
}

# -- main() --
std_exec "$@"