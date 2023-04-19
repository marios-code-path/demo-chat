#!/bin/bash

source ./util.sh
find_consul
# run memory core services
# with a deployment name of 'core_services'
# with key type of long
# with EDGE_SERVICES enabled
# with exposed rSOCKET ports on 6970
# with consul discovery enabled
./build-memory-core.sh -n core_services -k long -b rundocker -s file:/etc/keys -d -c $@
