#!/bin/bash

set -e

source ../shell-scripts/ports.sh
source ../shell-scripts/util.sh

export SPRING_ACTIVE_PROFILES=()

# Default build Profile has deploy
export BUILD_PROFILES=("deploy")

# Disable actuator by default
if [[ -z ${MANAGEMENT_ENDPOINTS} ]]; then
  export MANAGEMENT_ENDPOINTS=""
fi

while getopts ":d:lgsc:m:k:b:n:p:" o; do
  case $o in
    m)
      export MODULE=${OPTARG}
      ;;
    p)
      SPRING_ACTIVE_PROFILES+=(${OPTARG})
      ;;
    g)
      OPT_FLAGS=${OPT_FLAGS}" -Dlogging.level.io.rsocket.FrameLogger=DEBUG"
      ;;
    n)
      export DEPLOYMENT_NAME=${OPTARG}
      ;;
    c)
      export CERT_BASEPATH=${OPTARG}
      ;;
    k)
      export KEYSPACE_TYPE=${OPTARG}
      ;;
    b)
      export RUN_MAVEN_ARG=${OPTARG}
      ;;
    d)
      export DISCOVERY_TYPE=${OPTARG}

      if [[ ${DISCOVERY_TYPE} == "consul" ]]; then

        find_consul

        export DISCOVERY_ARGS="-Dspring.cloud.consul.host=${CONSUL_HOST} -Dspring.cloud.consul.port=${CONSUL_PORT} \
-Dspring.cloud.consul.discovery.enabled=true -Dapp.client.discovery=consul \
-Dspring.security.user.name=actuator -Dspring.security.user.password=actuator -Dspring.security.user.roles=ACTUATOR \
-Dspring.cloud.consul.discovery.preferIpAddress=true"
        ADDITIONAL_CONFIGS+="classpath:/config/client-consul.yml,"

        export INIT_CONFIG="-Dapp.kv.store=consul -Dapp.kv.prefix=/chat \
-Dapp.kv.rootkeys=rootkeys -Dapp.rootkeys.consume.scheme=kv \
-Dspring.cloud.consul.discovery.health-check-headers[0]='Authorization: Basic QXV0aG9yaXphdGlvbjogQmFzaWMg'"

        BUILD_PROFILES+=("register-consul" "client-consul")

        if [[ ! $MANAGEMENT_ENDPOINTS == *"health"* ]]; then
          MANAGEMENT_ENDPOINTS=${MANAGEMENT_ENDPOINTS}",health"
        fi
      fi

      if [[ ${DISCOVERY_TYPE} == "local" ]]; then
        export DISCOVERY_ARGS="-Dapp.client.discovery=properties \
-Dspring.cloud.consul.enabled=false \
-Dspring.cloud.service-registry.auto-registration.enabled=false \
-Dspring.cloud.consul.config.enabled=false \
-Dspring.cloud.consul.config.watch.enabled=false \
-Dspring.cloud.consul.discovery.enabled=false"
        ADDITIONAL_CONFIGS+="classpath:/config/client-local.yml,"
        export INIT_CONFIG="-Dapp.kv.store=none -Dapp.rootkeys.consume.scheme=http \
-Dapp.rootkeys.consume.source=http://localhost:6792"

        BUILD_PROFILES+=("client-local")
      fi
      ;;
    *)
      cat << CATZ
      specify:
      -m module name == demo chat module to build within
      -p profile == spring profile to activate
      -g == enable DEBUG on RSocket
      -n name == Name of container
      -d == enables Discovery with consul
      -l == discover locally
      -s == use TLS
      -k key_type == one of [long, uuid]
      -b build_arg == one of [build, runlocal, image, rundocker]
CATZ
      exit
      ;;
  esac
done

if [[ $(array_contains BUILD_PROFILES "client-local") == "0" &&
      $(array_contains BUILD_PROFILES "register_consul") == "0" ]]; then
  echo "You can't have both client-local and register_consul"
  exit 1
fi

if [[ -z ${KEYSTORE_PASS} ]]; then
  echo "KEYSTORE_PASS is not set"
  exit 1
fi

if [[ ! -z ${CLIENT_FLAGS} ]]; then
  export TLS_FLAGS="-Dapp.rsocket.transport.pkcs12 \
-Dapp.rsocket.transport.secure.truststore.path=${CERT_BASEPATH}/client_truststore.p12 \
-Dapp.rsocket.transport.secure.keystore.path=${CERT_BASEPATH}/client_keystore.p12 \
-Dapp.rsocket.transport.secure.keyfile.pass=${KEYSTORE_PASS}"
fi

if [[ -z ${TLS_FLAGS} ]]; then
  export TLS_FLAGS="-Dapp.rsocket.transport.insecure"
fi

if [[ ! -z ${MANAGEMENT_ENDPOINTS} ]]; then
  export ENDPOINT_FLAGS=$(expand_delimited "${MANAGEMENT_ENDPOINTS}" "-Dmanagement.endpoint.__.enabled=true ")
fi

cd ../$MODULE

export DEPLOYMENT_NAME=${DEPLOYMENT_NAME:=chat_client_deploy}
export DOCKER_CNAME="--name ${DEPLOYMENT_NAME}"
export APP_VERSION=0.0.1

export APP_SPRING_PROFILES="-Dspring.profiles.active="$(join_by "," "${SPRING_ACTIVE_PROFILES[@]}")
export MAVEN_PROFILES="-P"$(join_by "," ${BUILD_PROFILES[@]})

export MAIN_FLAGS="${MAIN_FLAGS} ${APP_SPRING_PROFILES} ${ENDPOINT_FLAGS} \
-Dapp.key.type=${KEYSPACE_TYPE} -Dapp.primary=${APP_PRIMARY}"

OPT_FLAGS+=" -Dspring.config.additional-location=${ADDITIONAL_CONFIGS%,}"

# makes no use of cloud configuration or config-maps
# That leading space is IMPORTANT ! DONT remove!
# TODO: difference between '-D' and '--'
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS}  \
${MAIN_FLAGS} \
${TLS_FLAGS} \
${PORTS_FLAGS} \
${INIT_CONFIG} \
${DISCOVERY_ARGS} \
${CLIENT_FLAGS} \
${SERVICE_FLAGS} \
${OPT_FLAGS}"

echo "MAVEN PROFILE: ${MAVEN_PROFILES}"

set -x

[[ $RUN_MAVEN_ARG == "" ]] && exit
[[ $RUN_MAVEN_ARG == "build" ]] && MAVEN_ARG="spring-boot:build-image"
[[ $RUN_MAVEN_ARG == "rundocker" ]] && MAVEN_ARG="spring-boot:build-image"
[[ $RUN_MAVEN_ARG == "runlocal" ]] && MAVEN_ARG="spring-boot:run"

mvn -DimageName=${APP_IMAGE_NAME} $MAVEN_PROFILES -DskipTests $MAVEN_ARG

[[ $RUN_MAVEN_ARG == "rundocker" ]] && docker run ${DOCKER_CNAME} ${DOCKER_ARGS} --rm $APP_IMAGE_NAME:$APP_VERSION