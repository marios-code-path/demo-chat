#!/bin/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

source $DIR/util.sh
source $DIR/ports.sh
set -e

export APP_VERSION=0.0.1
export TLS_FLAGS
export IMAGE_REPO_PREFIX=${IMAGE_REPO_PREFIX:="docker.io/library"}

if [[ -z ${SPRING_ACTIVE_PROFILES} ]]; then
  export SPRING_ACTIVE_PROFILES=""
fi

# Default build Profile has deploy
if [[ -z ${BUILD_PROFILES} ]]; then
  export BUILD_PROFILES="deploy,"
else
  BUILD_PROFILES+="deploy,"
fi

if [[ -z ${MANAGEMENT_ENDPOINTS} ]]; then
  export MANAGEMENT_ENDPOINTS=""
fi

if [[ -z ${KEY_VOLUME} ]]; then
  export KEY_VOLUME="demo-chat-server-keys"
fi

if [[ -z ${ROOTKEY_SOURCE_URI} ]]; then
  export ROOTKEY_SOURCE_URI=${ROOTKEY_SOURCE_URI:="http://${CORE_HOST:=127.0.0.1}:${CORE_MGMT_PORT}"}
fi

ADDITIONAL_CONFIGS+="classpath:/config/logging.yml,classpath:/config/management-defaults.yml,"

while getopts ":d:wlaoxgsc:m:i:k:b:n:p:" o; do
  case $o in
    w)
      export WEBSOCKET=true
      ;;
    o)
      export BAKE_OPTIONS=true
      ;;
    a)
      export NATIVE_BUILD=true
      ;;
    i)
      export INIT_PHASES=${OPTARG}
      ;;
    m)
      export MODULE=${OPTARG}
      ;;
    p)
      SPRING_ACTIVE_PROFILES+="${OPTARG},"
      ;;
    g)
      export DEBUG_ENABLED=true
      ;;
    n)
      export DEPLOYMENT_NAME=${OPTARG}
      ;;
    c)
      export CERT_DIR=${OPTARG}
      ;;
    k)
      export KEYSPACE_TYPE=${OPTARG}
      ;;
    b)
      export RUN_MAVEN_ARG=${OPTARG}
      ;;
    d)
      export DISCOVERY_TYPE=${OPTARG}
      ;;
    x)
      export SHOW_OPTIONS=1
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

if [[ ${BUILD_PROFILES} == *"client-local"* &&
      ${BUILD_PROFILES} == *"register_consul"* ]]; then
  echo "You can't have both client-local and register_consul"
  exit 1
fi

if [[ -z "${CERT_DIR}" && -z "${NO_SEC}" ]]; then
  echo "You must specify a certificate base-path with the -c option or CERT_DIR env."
  exit 1
fi

if [[ -z "${KEYSTORE_PASS}" && -z "${NO_SEC}" ]]; then
  echo "env KEYSTORE_PASS is not set"
  exit 1
fi

if [[ ${INIT_PHASES} == *"users"* ]]; then
  INIT_FLAGS+=" -Dapp.users.create=true"
  ADDITIONAL_CONFIGS+="classpath:/config/userinit.yml,"
fi

if [[ ${INIT_PHASES} == *"rootkeys"* ]]; then
  INIT_FLAGS+=" -Dapp.rootkeys.create=true"
fi

if [[ ! -z ${SERVICE_FLAGS}  && -z ${CLIENT_FLAGS} ]]; then
  if [[ -z ${NO_SEC} ]]; then
    TLS_FLAGS+=" -Dspring.rsocket.server.ssl.enabled=true \
-Dspring.rsocket.server.ssl.client-auth=none \
-Dspring.rsocket.server.ssl.protocol=TLS \
-Dspring.rsocket.server.ssl.enabled-protocols=TLSv1.2 \
-Dspring.rsocket.server.ssl.key-store=${CERT_DIR}/server_keystore.p12 \
-Dspring.rsocket.server.ssl.key-store-type=PKCS12 \
-Dspring.rsocket.server.ssl.key-store-password=${KEYSTORE_PASS}"
  else
    TLS_FLAGS+=" -Dspring.rsocket.server.ssl.enabled=false"
  fi

  if [[ ! -z ${WEBSOCKET} ]]; then
    SERVICE_FLAGS+=" -Dspring.rsocket.server.transport=websocket"
    SERVICE_FLAGS+=" -Dspring.rsocket.server.mapping-path=/"
    # BUILD_PROFILES+="websocket,"
  fi
fi

if [[ ! -z ${CLIENT_FLAGS} ]]; then
  if [[ -z ${NO_SEC} ]]; then
  TLS_FLAGS+=" -Dapp.rsocket.transport.security.type=pkcs12 \
-Dapp.rsocket.transport.security.truststore.path=${CERT_DIR}/client_truststore.p12 \
-Dapp.rsocket.transport.security.keystore.path=${CERT_DIR}/client_keystore.p12 \
-Dapp.rsocket.transport.security.keyfile.pass=${KEYSTORE_PASS}"
  else
  TLS_FLAGS+=" -Dapp.rsocket.transport.security.type=unprotected"
  fi

  if [[ ! -z ${WEBSOCKET} ]]; then
    CLIENT_FLAGS+=" -Dapp.rsocket.transport.websocket.enabled=true"
    BUILD_PROFILES+="websocket,"
  fi

  if [[ ! -z ${DEBUG} ]]; then
    OPT_FLAGS+="-Dlogging.level.com.demo.chat.client.rsocket=DEBUG \
-Dlogging.level.reactor.netty.http.client=DEBUG"
  fi
fi

if [[ ${DISCOVERY_TYPE} == "consul" ]]; then
  # Publish Root Keys TO Consul KV if we are initializing them here
  # Otherwise, consume them from Consul
  if [[ ${INIT_PHASES} == *"rootkeys"* ]]; then
        INIT_FLAGS+=" -Dapp.kv.store=consul -Dapp.kv.prefix=/chat -Dapp.kv.rootkeys=rootkeys \
-Dapp.rootkeys.publish.scheme=kv"
  else
        INIT_FLAGS+=" -Dapp.kv.store=consul -Dapp.kv.prefix=/chat \
-Dapp.kv.rootkeys=rootkeys -Dapp.rootkeys.consume.scheme=kv"
  fi

  find_consul

  # Turn on consul discovery &  registration
  DISCOVERY_FLAGS+=" -Dspring.cloud.consul.host=${CONSUL_HOST} \
-Dspring.cloud.consul.port=${CONSUL_PORT} \
-Dspring.cloud.consul.discovery.enabled=true \
-Dspring.cloud.consul.discovery.preferIpAddress=true \
-Dspring.cloud.consul.discovery.health-check-headers[0]='Authorization: Basic QXV0aG9yaXphdGlvbjogQmFzaWMg' \
-Dspring.security.user.name=actuator \
-Dspring.security.user.password=actuator \
-Dspring.security.user.roles=ACTUATOR"

  if [[ ! -z ${CLIENT_FLAGS} ]]; then
    ADDITIONAL_CONFIGS+="classpath:/config/client-rsocket-consul.yml,"
    DISCOVERY_FLAGS+=" -Dapp.client.discovery=consul"
    BUILD_PROFILES+="client-consul,"
  fi

  BUILD_PROFILES+="consul,"

  if [[ ! $MANAGEMENT_ENDPOINTS == *"health"* ]]; then
    MANAGEMENT_ENDPOINTS+=",health"
  fi
fi

if [[ ${DISCOVERY_TYPE} == "local" ]]; then

# Consume Rootkeys when not creating Rootkeys in a local discovery scenario
  if [[  ${INIT_PHASES} != *"rootkeys"* ]]; then
    INIT_FLAGS+="-Dapp.kv.store=none -Dapp.rootkeys.consume.scheme=http \
-Dapp.rootkeys.consume.source=${ROOTKEY_SOURCE_URI}"
  fi

  export DISCOVERY_FLAGS="-Dspring.cloud.consul.enabled=false \
-Dspring.cloud.service-registry.auto-registration.enabled=false \
-Dspring.cloud.consul.config.enabled=false \
-Dspring.cloud.consul.config.watch.enabled=false \
-Dspring.cloud.consul.discovery.enabled=false
-Dspring.security.user.name=actuator \
-Dspring.security.user.password=actuator \
-Dspring.security.user.roles=ACTUATOR
"

  if [[ ! -z ${CLIENT_FLAGS} ]]; then
    ADDITIONAL_CONFIGS+="classpath:/config/client-rsocket-local.yml,"
    DISCOVERY_FLAGS+=" -Dapp.client.discovery=properties"
    BUILD_PROFILES+="client-local,"
  fi
fi

if [[ ! -z ${MANAGEMENT_ENDPOINTS} ]]; then
  export ENDPOINT_FLAGS=$(expand_delimited "${MANAGEMENT_ENDPOINTS}" "-Dmanagement.endpoint.__.enabled=true ")
  ENDPOINT_FLAGS+=" -Dmanagement.endpoints.web.exposure.include=${MANAGEMENT_ENDPOINTS}"
fi

if [[ ! -z ${DEBUG_ENABLED} && ${DEBUG_ENABLED} == true ]]; then
      OPT_FLAGS+=" -Dlogging.level.io.rsocket.FrameLogger=DEBUG"
#      if [[ ${RUN_MAVEN_ARG} == "rundocker" ]]; then
#        DOCKER_ARGS+=" --env BPL_DEBUG_ENABLED=true --env BPL_DEBUG_PORT=${DEBUG_PORT} -p ${DEBUG_PORT}:${DEBUG_PORT}/tcp"
#      else
#        OPT_FLAGS+=" -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=${DEBUG_PORT}"
#      fi
fi

if [[ ! -z {NATIVE_BUILD} && ${NATIVE_BUILD} == true ]]; then
  #BUILD_PROFILES+="native,"
  echo "Native Builds are not supported at this time"
  echo "Use of @Profile, @Conditional... not supported"
  exit 1
fi

cd $DIR/../$MODULE

export SECURE_RANDOM="-Djava.security.egd=file:/dev/./urandom"
export APP_SPRING_PROFILES="-Dspring.profiles.active=${SPRING_ACTIVE_PROFILES%,}"
export MAVEN_PROFILES="-P${BUILD_PROFILES%,}"

export MAIN_FLAGS="${APP_SPRING_PROFILES} ${ENDPOINT_FLAGS} \
-Dapp.key.type=${KEYSPACE_TYPE} -Dapp.primary=${APP_PRIMARY} \
-Dspring.application.name=${DEPLOYMENT_NAME} ${SECURE_RANDOM}"


OPT_FLAGS+=" -Dspring.config.additional-location=${ADDITIONAL_CONFIGS%,}"
DOCKER_ARGS+=" --name ${DEPLOYMENT_NAME} -v ${KEY_VOLUME}:/etc/keys"

# makes no use of cloud configuration or config-maps
# That leading space is IMPORTANT ! DONT remove!
export JAVA_TOOL_OPTIONS=" ${JAVA_TOOL_OPTIONS}  \
${MAIN_FLAGS} \
${TLS_FLAGS} \
${PORTS_FLAGS} \
${INIT_FLAGS} \
${DISCOVERY_FLAGS} \
${CLIENT_FLAGS} \
${SERVICE_FLAGS} \
${OPT_FLAGS}"

export ENV_FILE=/tmp/${APP_IMAGE_NAME}-$$.env

echo "ENV_FILE=${ENV_FILE}"

cat > $ENV_FILE << EOF
JAVA_TOOL_OPTIONS=${JAVA_TOOL_OPTIONS}
EOF

if [[ ! -z ${SHOW_OPTIONS} ]]; then
  echo $JAVA_TOOL_OPTIONS
  exit 0
fi

if [[ ! -z ${BAKE_OPTIONS} ]]; then
  export JAVA_TOOL_OPTIONS=
  DOCKER_ARGS+=" --env-file ${ENV_FILE}"
fi

set -x

[[ $RUN_MAVEN_ARG == "" ]] && exit
[[ $RUN_MAVEN_ARG == "build" ]] && MAVEN_ARG="spring-boot:build-image"
[[ $RUN_MAVEN_ARG == "rundocker" ]] && MAVEN_ARG="spring-boot:build-image"
[[ $RUN_MAVEN_ARG == "runlocal" ]] && MAVEN_ARG="spring-boot:run"

mvn -DimageName=${APP_IMAGE_NAME} $MAVEN_PROFILES -DskipTests $MAVEN_ARG

[[ $RUN_MAVEN_ARG == "rundocker" ]] && docker run ${DOCKER_ARGS} --rm $APP_IMAGE_NAME:$APP_VERSION
