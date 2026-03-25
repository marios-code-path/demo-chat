#!/bin/bash

# Set script directory
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Source utility scripts
source "$DIR/util.sh"
source "$DIR/ports.sh"

# Stop on error
set -e

# Default environment variables
export APP_VERSION="0.0.1"
export IMAGE_REPO_PREFIX=${IMAGE_REPO_PREFIX:="docker.io/library"}
export NO_SEC=${NO_SEC:=false}
export DISCOVERY_TYPE="local"
export APP_SERVER_PROTO=${APP_SERVER_PROTO:="rsocket"}
export SPRING_ACTIVE_PROFILES=${SPRING_ACTIVE_PROFILES:=""}
export BACKEND=${BACKEND:=""}
export BUILD_PROFILES=${BUILD_PROFILES:="deploy,"}
export MANAGEMENT_ENDPOINTS=${MANAGEMENT_ENDPOINTS:=""}
export KEY_VOLUME=${KEY_VOLUME:="demo-chat-server-keys"}
export ROOTKEY_SOURCE_URI=${ROOTKEY_SOURCE_URI:="http://${CORE_HOST:=127.0.0.1}:${CORE_MGMT_PORT}"}
ADDITIONAL_CONFIGS+="classpath:/config/logging.yml,classpath:/config/management-defaults.yml,"

# Parse options
while getopts ":d:waoxge:s:b:c:m:i:k:b:n:p:" o; do
  case $o in
    s) export BACKEND=${OPTARG} ;;
    e) export EXPOSES=${OPTARG} ;;
    w) export WEBSOCKET=true ;;
    o) export BAKE_OPTIONS=true ;;
    a) export NATIVE_BUILD=true ;;
    i) export INIT_PHASES=${OPTARG} ;;
    m) export MODULE=${OPTARG} ;;
    p) SPRING_ACTIVE_PROFILES+="${OPTARG}," ;;
    g) export DEBUG_ENABLED=true ;;
    n) export DEPLOYMENT_NAME=${OPTARG} ;;
    c) [[ "${OPTARG}" == *"notls"* && "$NO_SEC" == "false" ]] && NO_SEC=true || export CERT_DIR=${OPTARG} ;;
    k) export KEYSPACE_TYPE=${OPTARG} ;;
    b) export RUN_MAVEN_ARG=${OPTARG} ;;
    d) DISCOVERY_TYPE=${OPTARG} ;;
    x) export SHOW_OPTIONS=1 ;;
    *) usage ;;
  esac
done

# Validate backend selection
if [[ -z $BACKEND ]]; then
  echo "You forgot to select a backend. Use '-s backend' where 'backend' can be 'memory', 'cassandra', and/or 'client'"
  exit 1
fi

# Configure backend and exposed services
configure_backend_and_services

# Validate certificates and security configuration
validate_certificates_and_security

# Initialize configurations
initialize_configs

# Configure discovery
configure_discovery

# Configure management endpoints
configure_management_endpoints

# Configure debugging options
configure_debugging_options

# Set up native build
setup_native_build

# Change directory to module
cd "$DIR/../$MODULE"

# Prepare environment variables
prepare_environment_variables

# Create environment file
create_env_file

# Show options if requested
[[ -n $SHOW_OPTIONS ]] && cat "$ENV_FILE" && exit 0

# Execute Maven commands
execute_maven_commands

# End of script
