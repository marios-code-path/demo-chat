#!/usr/bin/env bash
SEARCH_NAME=$1; shift
SEARCH_NAME=${SEARCH_NAME:="core-services-monolith"}

export CORE_HOST=`docker inspect -f '{{range.NetworkSettings.Networks}}{{.IPAddress}}{{end}}' ${SEARCH_NAME}`