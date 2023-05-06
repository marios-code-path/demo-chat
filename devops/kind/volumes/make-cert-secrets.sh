#!/bin/bash

set -x

export CHAT_ROOT=$1; shift
export CHAT_ROOT=${CHAT_ROOT:="../../../encrypt-keys"}

export SECRET_NAME=${SECRET_NAME:="chat-server-keystore"}

set -e

kubectl create secret generic $SECRET_NAME --from-file=${CHAT_ROOT}/server_keycert.jwk \
--from-file=${CHAT_ROOT}/server_truststore.p12 \
--from-file=${CHAT_ROOT}/server_keystore.p12

