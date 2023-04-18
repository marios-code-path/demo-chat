#!/usr/bin/env bash
source ../shell-scripts/ports.sh

export DISCOVERY_ARGS="-Dapp.client.discovery=properties -Dspring.config.additional-location=classpath:/config/client-local.yml"
export INIT_CONFIG="-Dapp.kv.store=none -Dapp.rootkeys.consume.scheme=http -Dapp.rootkeys.consume.source=http://localhost:6792"

while getopts ":dcgs:m:k:b:n:p:" o; do
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
    c)
      export DISCOVERY_ARGS="-Dspring.cloud.consul.host=${CONSUL_HOST} -Dspring.cloud.consul.port=${CONSUL_PORT} \
-Dspring.cloud.consul.discovery.enabled=true -Dapp.client.discovery=consul -Dspring.config.additional-location=classpath:/config/client-consul.yml,classpath:/config/application-consul.yml"
      export MAVEN_PROFILES="${MAVEN_PROFILES:=-P}register-consul"
      export INIT_CONFIG="-Dapp.kv.store=consul -Dapp.kv.prefix=/chat -Dapp.kv.rootkeys=rootkeys -Dapp.rootkeys.consume.scheme=kv"
      ;;
    s)
      if [[ -z ${KEYSTORE_PASS} ]]; then
        echo "KEYSTORE_PASS is not set"
        exit 1
      fi
      export TLS_FLAGS="-Dapp.rsocket.transport.pkcs12 \
-Dapp.rsocket.transport.secure.truststore.path=${OPTARG}/client_truststore.p12 \
-Dapp.rsocket.transport.secure.keystore.path=${OPTARG}/client_keystore.p12 \
-Dapp.rsocket.transport.secure.keyfile.pass=${KEYSTORE_PASS}"
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
      -c == enables Discovery with consul
      -k key_type == one of [long, uuid]
      -b build_arg == one of [build, runlocal, image, rundocker]
CATZ
      exit
      ;;
  esac
done

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
-Dapp.key.type=${KEYSPACE_TYPE} -Dapp.primary=${APP_PRIMARY} -Dmanagement.endpoints.enabled-by-default=false \
-Dspring.autoconfigure.exclude=org.springframework.boot.autoconfigure.rsocket.RSocketServerAutoConfiguration"
export DISCOVERY_FLAGS="${DISCOVERY_ARGS} -Dspring.cloud.service-registry.auto-registration.enabled=false \
-Dspring.cloud.consul.config.enabled=false"
export BOOTSTRAP_FLAGS="${INIT_CONFIG}"

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

[[ $RUN_MAVEN_ARG == "" ]] && exit
[[ $RUN_MAVEN_ARG == "build" ]] && MAVEN_ARG="spring-boot:build-image"
[[ $RUN_MAVEN_ARG == "rundocker" ]] && MAVEN_ARG="spring-boot:build-image"
[[ $RUN_MAVEN_ARG == "runlocal" ]] && MAVEN_ARG="spring-boot:run"

mvn -DimageName=${APP_IMAGE_NAME} $MAVEN_PROFILES -DskipTests $MAVEN_ARG

[[ $RUN_MAVEN_ARG == "rundocker" ]] && docker run ${DOCKER_CNAME} ${DOCKER_ARGS} --rm $APP_IMAGE_NAME:$APP_VERSION