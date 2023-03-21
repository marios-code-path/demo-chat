#!/usr/bin/env bash
cd ../chat-deploy-memory
source ../shell-scripts/ports.sh

while getopts :esicdon:k:b: o; do
  echo "$o is $OPTARG"
  case "$o" in
    s)
      export SECURITY_FLAGS="-Dapp.security.enabled"
      ;;
    i)
      export BOOTSTRAP_FLAGS="-Dapp.bootstrap=rootkeys app.rootkeys.consul.key=/chat/rootkeys"
      ;;
    n)
      export DEPLOYMENT_NAME=${OPTARG}
      ;;
    o)
      export NOBUILD=true
      ;;
    c)
      export DISCOVERY_FLAGS="-Dspring.cloud.consul.host=${CONSUL_HOST} -Dspring.cloud.consul.port=${CONSUL_PORT} \
-Dspring.config.import=optional:consul:"
      ;;
    k)
      export KEYSPACE_TYPE=${OPTARG}
      ;;
    b)
      export RUN_MAVEN_ARG=${OPTARG}
      ;;
    d)
      export DOCKER_ARGS="${DOCKER_ARGS} --expose 6790 -p 6790:6790/tcp --expose 6792 -p 6792:6792/tcp --expose 6791 -p 6791:6791/tcp"
      ;;
    *)
      cat << CATZ
      specify:
      -b build_arg (!!) == one of [build, runlocal, image, rundocker]
      -k key_type (!!) == one of [long, uuid]
      -n name == Name of container
      -s == enable endpoint security
      -c == enables Discovery with consul
      -d == export rsocket TCP to localhost
      -o == disables the build step

      (!!) required

      Consumed Environments:
      DOCKER_ARGS     == additional arguments passed into docker command
      DEPLOYMENT_NAME == name of the container that will be deployed
CATZ
      exit
      ;;
  esac
done

export DEPLOYMENT_NAME=${DEPLOYMENT_NAME:=core_services}
export DOCKER_ARGS="${DOCKER_ARGS} --name ${DEPLOYMENT_NAME}"

# how to auto-discover consul using dns alone!
export SPRING_PROFILE="exec-chat"
export APP_PRIMARY_NAME="core"
export APP_IMAGE_NAME="memory-${APP_PRIMARY_NAME}-services-rsocket"
export APP_MAIN_CLASS="com.demo.chat.deploy.memory.MemoryDeploymentApp"
export APP_VERSION=0.0.1
export MAVEN_PROFILE=-Pdeploy

export MAIN_FLAGS="-Dspring.profiles.active=${SPRING_PROFILE} \
-Dapp.primary=${APP_PRIMARY_NAME} -Dapp.key.type=${KEYSPACE_TYPE} \
-Dapp.rsocket.client.requester.factory=none -Dspring.shell.interactive.enabled=false"
export PORTS_FLAGS="-Dserver.port=$((CORE_PORT+1)) -Dmanagement.server.port=$((CORE_PORT+2)) -Dspring.rsocket.server.port=${CORE_PORT}"
export SERVICE_FLAGS="-Dapp.service.core.key -Dapp.service.core.pubsub -Dapp.service.core.index \
-Dapp.service.core.persistence -Dapp.service.core.secrets -Dapp.service.composite -Dapp.service.composite.auth"
export CONTROLLER_FLAGS="-Dapp.controller.persistence -Dapp.controller.index -Dapp.controller.key \
-Dapp.controller.pubsub -Dapp.controller.secrets -Dapp.controller.user -Dapp.controller.topic -Dapp.controller.message"


# makes no use of cloud configuration or config-maps
# TODO: difference between '-D' and '--'
export JAVA_TOOL_OPTIONS=" ${MAIN_FLAGS} \
${PORTS_FLAGS} \
${BOOTSTRAP_FLAGS} \
${SECURITY_FLAGS} \
${SERVICE_FLAGS} \
${CONTROLLER_FLAGS} \
${DISCOVERY_FLAGS}"

#set -x

[[ $RUN_MAVEN_ARG == "" ]] && exit
[[ $RUN_MAVEN_ARG == "build" ]] && MAVEN_ARG="spring-boot:build-image"
[[ $RUN_MAVEN_ARG == "rundocker" ]] && MAVEN_ARG="spring-boot:build-image"
[[ $RUN_MAVEN_ARG == "runlocal" ]] && MAVEN_ARG="spring-boot:run"

mvn -DimageName=${APP_IMAGE_NAME} -DmainClass=${APP_MAIN_CLASS} $MAVEN_ARG $MAVEN_PROFILE -DskipTests

# [[ $RUN_MAVEN_ARG == "rundocker" ]] && echo docker run ${DOCKER_CNAME} ${DOCKER_ARGS} --rm $APP_IMAGE_NAME:$APP_VERSION
[[ $RUN_MAVEN_ARG == "rundocker" ]] && docker run ${DOCKER_ARGS} --rm $APP_IMAGE_NAME:$APP_VERSION -f