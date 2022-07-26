#!/usr/bin/env bash
cd ../chat-deploy-memory
source ../shell-scripts/ports.sh

while getopts ":cd:k:b:n:o" o; do
  case $o in
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
    *)
      cat << CATZ
      specify:
      -n name == Name of container
      -o == disables the build step
      -c == enables Discovery with consul
      -k key_type == one of [long, uuid]
      -b build_arg == one of [build, runlocal, image, rundocker]
CATZ
      exit
      ;;
  esac
done

export DEPLOYMENT_NAME=${DEPLOYMENT_NAME:=core_services}
export DOCKER_ARGS="${DOCKER_ARGS} --name ${DEPLOYMENT_NAME}"

# how to auto-discover consul using dns alone!
export SPRING_PROFILE="default"
export APP_PRIMARY="core"
export APP_IMAGE_NAME="core-services-monolith"
export APP_MAIN_CLASS="com.demo.chat.deploy.memory.App"
export APP_VERSION=0.0.1

# makes no use of cloud configuration or config-maps
# TODO: difference between '-D' and '--'
export JAVA_TOOL_OPTIONS=" -Dspring.profiles.active=${SPRING_PROFILE} \
-Dserver.port=$((CORE_PORT+1)) -Dspring.rsocket.server.port=${CORE_PORT} \
-Dapp.service.core.key=${KEYSPACE_TYPE} -Dapp.service.core.pubsub -Dapp.service.core.index \
-Dapp.service.core.persistence -Dapp.service.edge.topic \
-Dapp.service.edge.user -Dapp.service.edge.messaging \
-Dapp.primary=core -Dapp.rsocket.client.requester.factory=default ${DISCOVERY_ARGS}"

[[ $RUN_MAVEN_ARG == "" ]] && exit
[[ $RUN_MAVEN_ARG == "build" ]] && MAVEN_ARG="spring-boot:build-image"
[[ $RUN_MAVEN_ARG == "rundocker" ]] && MAVEN_ARG="spring-boot:build-image"
[[ $RUN_MAVEN_ARG == "runlocal" ]] && MAVEN_ARG="spring-boot:run"

[[ $NOBUILD == "false" ]] && mvn -DimageName=${APP_IMAGE_NAME} -DmainClass=${APP_MAIN_CLASS} $MAVEN_ARG

[[ $RUN_MAVEN_ARG == "rundocker" ]] && docker run ${DOCKER_ARGS}  --rm -d $APP_IMAGE_NAME:$APP_VERSION