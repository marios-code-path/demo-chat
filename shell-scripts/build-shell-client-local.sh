#!/usr/bin/env bash
cd ../chat-shell
source ../shell-scripts/ports.sh

export DISCOVERY_ARGS="-Dapp.client.discovery=properties -Dspring.config.additional-location=classpath:/client-local.yml"
export SPRING_PROFILE="shell"
export INIT_CONFIG="-Dapp.kv.store=none -Dapp.rootkeys.consume.scheme=http -Dapp.rootkeys.consume.source=http://localhost:6792"

while getopts ":sdcgk:b:n:p:" o; do
  case $o in
    p)
      export SPRING_PROFILE="${SPRING_PROFILE},${OPTARG}"
      ;;
    g)
      export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS} -Dlogging.level.io.rsocket.FrameLogger=DEBUG"
      ;;
    n)
      export DEPLOYMENT_NAME=${OPTARG}
      ;;
    c)
      export DISCOVERY_ARGS="-Dspring.cloud.consul.host=${CONSUL_HOST} -Dspring.cloud.consul.port=${CONSUL_PORT} \
-Dspring.cloud.consul.discovery.enabled=true -Dapp.client.discovery=consul -Dspring.config.additional-location=classpath:/client-consul.yml"
      export MAVEN_PROFILES="-P discovery-consul"
      export SPRING_PROFILE="${SPRING_PROFILE},consul"
      export INIT_CONFIG="-Dapp.kv.store=consul -Dapp.kv.prefix=/chat -Dapp.kv.rootkeys=rootkeys -Dapp.rootkeys.consume.scheme=kv"
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

export DEPLOYMENT_NAME=${DEPLOYMENT_NAME:=chat_shell}
export DOCKER_CNAME="--name ${DEPLOYMENT_NAME}"

export APP_PRIMARY="shell"
export APP_IMAGE_NAME="chat-shell"
export APP_MAIN_CLASS="com.demo.chat.init.BaseApp"
export APP_VERSION=0.0.1
# Say when discovery_consul is deactivated, we don't want to pass in the consul host and port, or configure discovery
# and KV store

export MAIN_FLAGS="-Dspring.profiles.active=${SPRING_PROFILE} -Dspring.shell.interactive.enabled=true \
-Dapp.key.type=${KEYSPACE_TYPE} -Dapp.primary=shell -Dmanagement.endpoints.enabled-by-default=false"
export CLIENT_FLAGS="-Dapp.client.protocol=rsocket \
-Dapp.rsocket.transport.unprotected -Dapp.client.rsocket.core.key -Dapp.client.config=properties \
-Dapp.client.rsocket.core.persistence -Dapp.client.rsocket.core.index -Dapp.client.rsocket.core.pubsub \
-Dapp.client.rsocket.core.secrets -Dapp.client.rsocket.composite.user -Dapp.client.rsocket.composite.message \
-Dapp.client.rsocket.composite.topic"
export DISCOVERY_FLAGS="${DISCOVERY_ARGS} -Dspring.cloud.service-registry.auto-registration.enabled=false \
-Dspring.cloud.consul.config.enabled=false"
export SERVICE_FLAGS="-Dapp.service.core.key -Dapp.service.composite.auth"
export BOOTSTRAP_FLAGS="${INIT_CONFIG}"
export PORTS_FLAGS="-Dserver.port=0"

# makes no use of cloud configuration or config-maps
# That leading space is IMPORTANT ! DONT remove!
# TODO: difference between '-D' and '--'
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS}  \
${MAIN_FLAGS} \
${PORTS_FLAGS} \
${BOOTSTRAP_FLAGS} \
${DISCOVERY_FLAGS} \
${CLIENT_FLAGS} \
${SERVICE_FLAGS}"

set -x

[[ $RUN_MAVEN_ARG == "" ]] && exit
[[ $RUN_MAVEN_ARG == "build" ]] && MAVEN_ARG="spring-boot:build-image"
[[ $RUN_MAVEN_ARG == "rundocker" ]] && MAVEN_ARG="spring-boot:build-image"
[[ $RUN_MAVEN_ARG == "runlocal" ]] && MAVEN_ARG="spring-boot:run"

mvn -DimageName=${APP_IMAGE_NAME} -DmainClass=${APP_MAIN_CLASS} $MAVEN_ARG $MAVEN_PROFILES -DskipTests

[[ $RUN_MAVEN_ARG == "rundocker" ]] && docker run ${DOCKER_CNAME} ${DOCKER_ARGS} --rm -d $APP_IMAGE_NAME:$APP_VERSION