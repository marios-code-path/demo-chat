#!/bin/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

if [[ -z ${GHCR_USER} || -z ${GHCR_TOKEN} ]]; then
  echo A username and token are required to access the GitHub Container Registry
  echo set them in the environment as GHCR_USER and GHCR_TOKEN.
  exit 1
fi

set -x

export CHAT_ROOT=$1; shift
export CHAT_ROOT=${CHAT_ROOT:="${DIR}/../../../encrypt-keys"}

export SECRET_NAME=${SECRET_NAME:="github-container-registry"}

export NAMESPACE=${NAMESPACE:="default"}

set -e

kubectl create secret docker-registry $SECRET_NAME --namespace=$NAMESPACE \
--docker-server=ghcr.io --docker-username=$GHCR_USER --docker-password=$GHCR_TOKEN

cat <<EOF
NOTE: You must add the following to your deployment yaml:
      imagePullSecrets:
        - name: ${SECRET_NAME}
EOF