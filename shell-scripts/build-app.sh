#!/bin/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

source $DIR/util.sh
source $DIR/ports.sh
set -e

export APP_VERSION=0.0.1
export TLS_FLAGS
export IMAGE_REPO_PREFIX=${IMAGE_REPO_PREFIX:="docker.io/library"}
export NO_SEC=false
export DISCOVERY_TYPE=local

if [[ -z ${SPRING_ACTIVE_PROFILES} ]]; then
  export SPRING_ACTIVE_PROFILES=""
fi

if [[ -z ${BACKEND} ]]; then
  export BACKEND
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

while getopts ":d:waoxge:s:b:c:m:i:k:b:n:p:" o; do
  case $o in
    s)
      export BACKEND=${OPTARG}
      ;;
    e)
      export EXPOSES=${OPTARG}
      ;;
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
      if [[ ${OPTARG} == *"notls"* ]]; then
        NO_SEC=true
      else
        export CERT_DIR=${OPTARG}
      fi
      ;;
    k)
      export KEYSPACE_TYPE=${OPTARG}
      ;;
    b)
      export RUN_MAVEN_ARG=${OPTARG}
      ;;
    d)
      DISCOVERY_TYPE=${OPTARG}
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
      -a == Native
      -n name == Name of container
      -d discovery == one of [local, properties, consul]
      -k key_type == one of [long, uuid]
      -b build_arg == one of [build, runlocal, image, rundocker]
      -c CERT_DIR placeholder == set location of certificate profiles
      -s BACKEND == one of [memory, cassandra, redis, client]
      -e Expose == one of [http, rsocket, shell]

      Additional Environment Variables:
      KEYSTORE_PASS = password for keystore when using TLS
      CERT_DIR = directory where certs are stored
CATZ
      exit
      ;;
  esac
done

if [[ -z ${BACKEND} ]]; then
cat << BACKSEL
you forgot to select a backend. use '-s backend'
where 'backend' can be 'memory' and 'cassandra'
BACKSEL
exit 1
fi

export BKEYS=("memory" "cassandra" "client")
export BVALS=("memory-backend" "cassandra-backend" "client-backend")

for key in ${!BKEYS[@]}; do
    if [[ ! -z $BACKEND && $BACKEND == *${BKEYS[$key]}* ]]; then
      BUILD_PROFILES+="${BVALS[$key]}",
    fi
done

export EXKEYS=("http" "rsocket" "shell" "gateway")
export EXVALS=("expose-webflux" "expose-rsocket" "shell" "expose-gateway")

for key in ${!EXKEYS[@]}; do
    if [[  $EXPOSES == *${EXKEYS[$key]}* ]]; then
        BUILD_PROFILES+="${EXVALS[$key]}",
    fi
done

if [[ ${BUILD_PROFILES} == *"client-local"* &&
      ${BUILD_PROFILES} == *"register-consul"* ]]; then
  echo "You can't have both client-local and register_consul"
  exit 1
fi

if [[ -z "${CERT_DIR}" && ${NO_SEC} == *"false"* ]]; then
  echo "You must specify a certificate base-path with the -c option or CERT_DIR env."
  echo "You can turn off tls by setting -c notls."
  exit 1
fi

if [[ -z "${KEYSTORE_PASS}" && ${NO_SEC} == *"false"* ]]; then
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
  OPT_FLAGS+=" -Dapp.service.security.userdetails"

  if [[ ${NO_SEC} == *"false"* ]]; then
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

  if [[ ${NO_SEC} == *"false"* ]]; then
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

if [[ ! -z ${DISCOVERY_TYPE} && ! -z ${CLIENT_FLAGS} ]]; then
      if [[ ${EXPOSES} == *"rsocket"* ]]; then
        ADDITIONAL_CONFIGS+="classpath:/config/client-rsocket-${DISCOVERY_TYPE}.yml,"
      fi
      # TODO WE HAVE TO IMPLEMENT
      if [[ ${EXPOSES} == *"http"* ]]; then
        ADDITIONAL_CONFIGS+="classpath:/config/client-http-${DISCOVERY_TYPE}.yml,"
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
  fi
fi

if [[ ! -z ${MANAGEMENT_ENDPOINTS} ]]; then
  export ENDPOINT_FLAGS=$(expand_delimited "${MANAGEMENT_ENDPOINTS}" "-Dmanagement.endpoint.__.enabled=true ")
  ENDPOINT_FLAGS+=" -Dmanagement.endpoints.web.exposure.include=${MANAGEMENT_ENDPOINTS}"
fi

if [[ ! -z ${DEBUG_ENABLED} && ${DEBUG_ENABLED} == true ]]; then
      OPT_FLAGS+=" -Dlogging.level.io.rsocket.FrameLogger=DEBUG"
      if [[ ${RUN_MAVEN_ARG} == "rundocker" ]]; then
        DOCKER_ARGS+=" --env BPL_DEBUG_ENABLED=true --env BPL_DEBUG_PORT=${DEBUG_PORT} -p ${DEBUG_PORT}:${DEBUG_PORT}/tcp"
      else
        SPRING_JVM_ARGUMENTS+="-agentlib:jdwp=transport=dt_socket,server=y,suspend=${DEBUG_SUSPEND:=n},address=${DEBUG_PORT}"
      fi
fi

if [[ ! -z {NATIVE_BUILD} && ${NATIVE_BUILD} == true ]]; then
  #BUILD_PROFILES+="native,"
  echo "Native Builds are not supported at this time"
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

RUN_ARGS="-Dspring-boot.run.arguments=\"${SPRING_RUN_ARGUMENTS}\" -Dspring-boot.run.jvmArguments=\"${SPRING_JVM_ARGUMENTS}\""

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
${OPT_FLAGS} \
${RUN_ARGS}"

export ENV_FILE=/tmp/${APP_IMAGE_NAME}-$$.env

getRunCommands

echo "ENV_FILE=${ENV_FILE}"

if [[ ! -z ${SHOW_OPTIONS} ]]; then
  cat $ENV_FILE
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

echo $$