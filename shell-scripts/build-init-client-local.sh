#!/usr/bin/env bash
cd ../chat-init
source ../shell-scripts/ports.sh

while getopts ":cgk:b:n:p:" o; do
  case $o in
    p)
      export SPRING_PROFILE=${OPTARG}
      ;;
    g)
      export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS} -Dlogging.level.io.rsocket.FrameLogger=DEBUG"
      ;;
    n)
      export DEPLOYMENT_NAME=${OPTARG}
      ;;
    c)
      export DISCOVERY_ARGS="-Dspring.cloud.consul.host=${CONSUL_HOST} -Dspring.cloud.consul.port=${CONSUL_PORT}"
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

export DEPLOYMENT_NAME=${DEPLOYMENT_NAME:=chat_init}
export DOCKER_CNAME="--name ${DEPLOYMENT_NAME}"

# how to auto-discover consul using dns alone!
export APP_PRIMARY="init"
export APP_IMAGE_NAME="chat-init"
export APP_MAIN_CLASS="com.demo.chat.init.BaseApp"
export APP_VERSION=0.0.1
export MAVEN_PROFILE

# makes no use of cloud configuration or config-maps
# That leading space is IMPORTANT ! DONT remove!
# TODO: difference between '-D' and '--'
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS} -Dspring.profiles.active=${SPRING_PROFILE} \
-Dapp.service.core.key=${KEYSPACE_TYPE} -Dapp.primary=init \
-Dapp.rsocket.transport.unprotected -Dapp.client.rsocket.core.key -Dapp.init.bootstrap=true \
-Dapp.client.rsocket.core.persistence -Dapp.client.rsocket.core.index -Dapp.client.rsocket.core.pubsub \
-Dapp.client.rsocket.core.secrets -Dapp.client.rsocket.composite.user -Dapp.client.rsocket.composite.message \
-Dapp.client.rsocket.composite.topic -Dapp.service.composite.auth \
-Dapp.client.rsocket.discovery.default -Dspring.cloud.service-registry.auto-registration.enabled=false \
-Dspring.cloud.consul.config.enabled=false -Dspring.rsocket.server.port=7890 -Dserver.port=0 \
-Dspring.shell.interactive.enabled=false -Dmanagement.endpoints.enabled-by-default=false"

set -x

[[ $RUN_MAVEN_ARG == "" ]] && exit
[[ $RUN_MAVEN_ARG == "build" ]] && MAVEN_ARG="spring-boot:build-image"
[[ $RUN_MAVEN_ARG == "rundocker" ]] && MAVEN_ARG="spring-boot:build-image"
[[ $RUN_MAVEN_ARG == "runlocal" ]] && MAVEN_ARG="spring-boot:run"

mvn -DimageName=${APP_IMAGE_NAME} -DmainClass=${APP_MAIN_CLASS} $MAVEN_ARG $MAVEN_PROFILE -DskipTests

[[ $RUN_MAVEN_ARG == "rundocker" ]] && docker run ${DOCKER_CNAME} ${DOCKER_ARGS} --rm -d $APP_IMAGE_NAME:$APP_VERSION