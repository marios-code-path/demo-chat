#!/usr/bin/env bash
source ../shell-scripts/ports.sh
source ../shell-scripts/util.sh

export SPRING_ACTIVE_PROFILES=()

export BUILD_PROFILES=()
BUILD_PROFILES+=("deploy")

while getopts ":dlgs:m:k:b:n:p:" o; do
  case $o in
    m)
      export MODULE=${OPTARG}
      ;;
    p)
      export SPRING_PROFILE="${OPTARG}"
      ;;
    g)
      export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS} -Dlogging.level.io.rsocket.FrameLogger=DEBUG"
      ;;
    n)
      export DEPLOYMENT_NAME=${OPTARG}
      ;;
    d)
      export DISCOVERY_ARGS="-Dspring.cloud.consul.host=${CONSUL_HOST} -Dspring.cloud.consul.port=${CONSUL_PORT} \
-Dspring.cloud.consul.discovery.enabled=true -Dapp.client.discovery=consul \
-Dspring.config.additional-location=classpath:/config/client-consul.yml,\
classpath:/config/application-http-consul.yml \
-Dspring.security.user.name=actuator -Dspring.security.user.password=actuator -Dspring.security.user.roles=ACTUATOR"

      export INIT_CONFIG="-Dapp.kv.store=consul -Dapp.kv.prefix=/chat \
-Dapp.kv.rootkeys=rootkeys -Dapp.rootkeys.consume.scheme=kv \
-Dspring.cloud.consul.discovery.health-check-headers[0]='Authorization: Basic QXV0aG9yaXphdGlvbjogQmFzaWMg'"

      BUILD_PROFILES+=("register-consul" "client-consul")
      ;;
    l)
      export DISCOVERY_ARGS="-Dapp.client.discovery=properties \
-Dspring.cloud.consul.enabled=false \
-Dspring.config.additional-location=classpath:/config/client-local.yml \
-Dspring.cloud.service-registry.auto-registration.enabled=false \
-Dspring.cloud.consul.config.enabled=false \
-Dspring.cloud.consul.config.watch.enabled=false \
-Dspring.cloud.consul.discovery.enabled=false"

      export INIT_CONFIG="-Dapp.kv.store=none -Dapp.rootkeys.consume.scheme=http \
-Dapp.rootkeys.consume.source=http://localhost:6792"

      BUILD_PROFILES+=("client-local")
      ;;
    s)
      if [[ -z ${KEYSTORE_PASS} ]]; then
        echo "KEYSTORE_PASS is not set"
        exit 1
      fi
      if [[ ${APP_PROTO} == "rsocket" ]]; then
      export TLS_FLAGS="-Dapp.rsocket.transport.pkcs12 \
-Dapp.rsocket.transport.secure.truststore.path=${OPTARG}/client_truststore.p12 \
-Dapp.rsocket.transport.secure.keystore.path=${OPTARG}/client_keystore.p12 \
-Dapp.rsocket.transport.secure.keyfile.pass=${KEYSTORE_PASS}"
      fi
      ;;
    k)
      export KEYSPACE_TYPE=${OPTARG}
      ;;
    b)
      export RUN_MAVEN_ARG=${OPTARG}
      ;;
    *)
      cat << CATZ
      specify:
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


cd ../$MODULE

export DEPLOYMENT_NAME=${DEPLOYMENT_NAME:=chat_client_deploy}
export DOCKER_CNAME="--name ${DEPLOYMENT_NAME}"
export APP_VERSION=0.0.1
# Say when discovery_consul is deactivated, we don't want to pass in the consul host and port, or configure discovery
# and KV store

if [[ -z ${TLS_FLAGS} ]]; then
  export TLS_FLAGS="-Dapp.rsocket.transport.insecure"
fi

export MAIN_FLAGS="${MAIN_FLAGS} -Dspring.profiles.active=${SPRING_PROFILE} \
-Dapp.key.type=${KEYSPACE_TYPE} -Dapp.primary=${APP_PRIMARY} -Dmanagement.endpoints.enabled-by-default=true \
-Dspring.autoconfigure.exclude=org.springframework.boot.autoconfigure.rsocket.RSocketServerAutoConfiguration"
export DISCOVERY_FLAGS="${DISCOVERY_ARGS}"
export BOOTSTRAP_FLAGS="${INIT_CONFIG}"
export MAVEN_PROFILES="-P"$(join_by "," ${BUILD_PROFILES[@]})

# makes no use of cloud configuration or config-maps
# That leading space is IMPORTANT ! DONT remove!
# TODO: difference between '-D' and '--'
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS}  \
${MAIN_FLAGS} \
${TLS_FLAGS} \
${PORTS_FLAGS} \
${BOOTSTRAP_FLAGS} \
${DISCOVERY_FLAGS} \
${CLIENT_FLAGS} \
${SERVICE_FLAGS}"

echo "MAVEN PROFILE: ${MAVEN_PROFILES}"

set -x
set -e

[[ $RUN_MAVEN_ARG == "" ]] && exit
[[ $RUN_MAVEN_ARG == "build" ]] && MAVEN_ARG="spring-boot:build-image"
[[ $RUN_MAVEN_ARG == "rundocker" ]] && MAVEN_ARG="spring-boot:build-image"
[[ $RUN_MAVEN_ARG == "runlocal" ]] && MAVEN_ARG="spring-boot:run"

mvn -DimageName=${APP_IMAGE_NAME} $MAVEN_PROFILES -DskipTests $MAVEN_ARG

[[ $RUN_MAVEN_ARG == "rundocker" ]] && docker run ${DOCKER_CNAME} ${DOCKER_ARGS} --rm $APP_IMAGE_NAME:$APP_VERSION