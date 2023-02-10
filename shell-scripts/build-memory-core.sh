#!/usr/bin/env bash
cd ../chat-deploy-memory
source ../shell-scripts/ports.sh

while getopts :ecdon:k:b: o; do
  echo "$o is $OPTARG"
  case "$o" in
    n)
      export DEPLOYMENT_NAME=${OPTARG}
      ;;
    o)
      export NOBUILD=true
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
    e)
      export EDGE_SERVICES="${EDGE_SERVICES} -Dapp.service.edge.user -Dapp.service.edge.topic -Dapp.service.edge.message"
      ;;
    d)
      export DOCKER_ARGS="${DOCKER_ARGS} --expose 6790 -p 6790:6790/tcp"
      ;;
    *)
      cat << CATZ
      specify:
      -b build_arg (!!) == one of [build, runlocal, image, rundocker]
      -k key_type (!!) == one of [long, uuid]
      -n name == Name of container
      -c == enables Discovery with consul
      -d == export rsocket TCP to localhost
      -e == enables core edge (message, user, topic) services
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
export APP_PRIMARY="core"
export APP_IMAGE_NAME="core-services"
export APP_MAIN_CLASS="com.demo.chat.deploy.memory.App"
export APP_VERSION=0.0.1
export MAVEN_PROFILE=-Pmemory,deploy

# makes no use of cloud configuration or config-maps
# TODO: difference between '-D' and '--'
export JAVA_TOOL_OPTIONS=" -Dspring.profiles.active=${SPRING_PROFILE} -Dserver.port=$((CORE_PORT+1)) \
-Dmanagement.server.port=$((CORE_PORT+2)) -Dspring.rsocket.server.port=${CORE_PORT} \
-Dapp.primary=core -Dspring.shell.interactive.enabled=false \
-Dapp.service.core.key=${KEYSPACE_TYPE} -Dapp.service.core.pubsub -Dapp.service.core.index \
-Dapp.service.core.persistence -Dapp.service.core.secrets ${EDGE_SERVICES}\
-Dapp.rsocket.client.requester.factory=default ${DISCOVERY_ARGS}"

#set -x

[[ $RUN_MAVEN_ARG == "" ]] && exit
[[ $RUN_MAVEN_ARG == "build" ]] && MAVEN_ARG="spring-boot:build-image"
[[ $RUN_MAVEN_ARG == "rundocker" ]] && MAVEN_ARG="spring-boot:build-image"
[[ $RUN_MAVEN_ARG == "runlocal" ]] && MAVEN_ARG="spring-boot:run"

mvn -DimageName=${APP_IMAGE_NAME} -DmainClass=${APP_MAIN_CLASS} $MAVEN_ARG $MAVEN_PROFILE

[[ $RUN_MAVEN_ARG == "rundocker" ]] && docker run ${DOCKER_ARGS} --rm -d $APP_IMAGE_NAME:$APP_VERSION