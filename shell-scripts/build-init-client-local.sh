#!/usr/bin/env bash
cd ../chat-init
source ../shell-scripts/ports.sh

while getopts ":ck:b:n:t:o" o; do
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
    t)
      export CORE_SERVICES_IMAGE=${OPTARG}
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
      -t image name == set CONNECT_HOST
      -b build_arg == one of [build, runlocal, image, rundocker]
CATZ
      exit
      ;;
  esac
done

if [ ! -z ${CORE_SERVICES_IMAGE} ]; then
source ../shell-scripts/get_core_host.sh ${CORE_SERVICES_IMAGE}
#export DISCOVERY_ARGS="-Dapp.rsocket.client.config.pubsub.dest=${CORE_HOST} \
#-Dapp.rsocket.client.config.index.dest=${CORE_HOST} \
#-Dapp.rsocket.client.config.persistence.dest=${CORE_HOST} \
#-Dapp.rsocket.client.config.key.dest=${CORE_HOST} \
#-Dapp.rsocket.client.config.message.dest=${CORE_HOST} \
#-Dapp.rsocket.client.config.topic.dest=${CORE_HOST} \
#-Dapp.rsocket.client.config.user.dest=${CORE_HOST} "
fi

export DEPLOYMENT_NAME=${DEPLOYMENT_NAME:=chat_init}
export DOCKER_CNAME="--name ${DEPLOYMENT_NAME}"

# how to auto-discover consul using dns alone!
export SPRING_PROFILE="shell"
export APP_PRIMARY="init"
export APP_IMAGE_NAME="chat-init"
export APP_MAIN_CLASS="com.demo.chat.init.App"
export APP_VERSION=0.0.1

# makes no use of cloud configuration or config-maps
# That leading space is IMPORTANT ! DONT remove!
# TODO: difference between '-D' and '--'
export JAVA_TOOL_OPTIONS=" -Dspring.profiles.active=${SPRING_PROFILE} \
-Dapp.service.core.key=${KEYSPACE_TYPE} -Dapp.client.rsocket.core.pubsub -Dapp.client.rsocket.core.index \
-Dapp.client.rsocket.core.persistence -Dapp.client.rsocket.core.key \
-Dapp.rsocket.client.requester.factory=default ${DISCOVERY_ARGS}"

[[ $RUN_MAVEN_ARG == "" ]] && exit
[[ $RUN_MAVEN_ARG == "build" ]] && MAVEN_ARG="spring-boot:build-image"
[[ $RUN_MAVEN_ARG == "rundocker" ]] && MAVEN_ARG="spring-boot:build-image"
[[ $RUN_MAVEN_ARG == "runlocal" ]] && MAVEN_ARG="spring-boot:run"

[[ $NOBUILD == "false" ]] && mvn -DimageName=${APP_IMAGE_NAME} -DmainClass=${APP_MAIN_CLASS} $MAVEN_ARG

[[ $RUN_MAVEN_ARG == "rundocker" ]] && docker run ${DOCKER_CNAME} ${DOCKER_ARGS} --rm $APP_IMAGE_NAME:$APP_VERSION