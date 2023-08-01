#!/bin/bash

export SCHEMA_SELECT=$1; shift

if [[ -z $SCHEMA_SELECT ]]; then
  cat <<EOF
  Usage: $0 <schema>
  Where <schema> is one of:
    - keyspace-uuid
    - keyspace-long
    - truncate-uuid
    - truncate-long
EOF

  exit 1
fi

if [[ -z $CASSANDRA_PASSWORD ]]; then
  cat <<EOFF
please set CASSANDRA_PASSWORD to the password for the cassandra user
EOFF
  exit 1
fi

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

set -x

export CHAT_ROOT=$1; shift
export SCHEMA_PATH=${CHAT_ROOT:="${DIR}/../../../shared-resources-cassandra"}

RESOURCE_PATH="$( cd ${SCHEMA_PATH} && pwd )"

export VOLUME_NAME="demo-chat-cql-schema"

set -e

kubectl create secret generic demo-chat-cassandra-user --from-literal=password=$CASSANDRA_PASSWORD

kubectl create configmap ${VOLUME_NAME} --from-file=schema.cql=${RESOURCE_PATH}/src/main/resources/$SCHEMA_SELECT.cql