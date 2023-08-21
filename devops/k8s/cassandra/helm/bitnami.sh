#!/bin/bash

export SCHEMA_SELECT=$1; shift

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

export CHAT_ROOT=${CHAT_ROOT:="${DIR}/../../../.."}

SCRIPT_PATH="${CHAT_ROOT}/shell-scripts"

SCHEMA_PATH="${CHAT_ROOT}/shared-resources-cassandra"

RESOURCE_PATH="$( cd ${SCHEMA_PATH} && pwd )"

export CASSANDRA_USER="demochat"

source $SCRIPT_PATH/util.sh

if [[ -z $SCHEMA_SELECT ]]; then
  cat <<EOF
  Usage: $0 <schema> <ddl_strategy>
  Where <schema> is one of:
    - uuid
    - long
  And <ddl_strategy> is one of:
    - secret
    - configmap
  **  CASSANDRA_PASSWORD must be set in the environment
EOF
  exit 1
fi

if [[ -z $CASSANDRA_PASSWORD ]]; then
  cat <<EOFF
please set CASSANDRA_PASSWORD to the password for the cassandra user
EOFF
  exit 1
fi

export VOLUME_NAME="demo-chat-cql-schema"

set -e

function undo() {
helm delete my-release
kubectl delete secret ${VOLUME_NAME}
kubectl delete pvc --all
}

function secret() {
# Create the schema file and user creation script
# Then copy them to the secret
FILECONTENT=$( cat ${RESOURCE_PATH}/src/main/resources/keyspace-$SCHEMA_SELECT.cql )
CREATEUSER="CREATE USER IF NOT EXISTS $CASSANDRA_USER WITH PASSWORD '$CASSANDRA_PASSWORD';"
GRANTUSER="GRANT ALL ON KEYSPACE chat_$SCHEMA_SELECT TO $CASSANDRA_USER;"

echo $FILECONTENT$CREATEUSER$GRANTUSER | kubectl create secret generic ${VOLUME_NAME} --from-file=schema.cql=/dev/stdin

helm install my-release oci://registry-1.docker.io/bitnamicharts/cassandra --set initDBSecret=${VOLUME_NAME}
}

function configmap() {

kubectl create configmap ${VOLUME_NAME} --from-file=schema.cql=${RESOURCE_PATH}/src/main/resources/$SCHEMA_SELECT.cql
kubectl create secret generic demo-chat-cassandra-user --from-literal=cassandra-password=$CASSANDRA_PASSWORD
helm install my-release oci://registry-1.docker.io/bitnamicharts/cassandra \
--set initDBSecret=demo-chat-cql-schema
}


# ---- main() ----

std_exec $@