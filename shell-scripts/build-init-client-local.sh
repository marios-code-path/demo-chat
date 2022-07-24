cd ../chat-init
export KEYSPACE_TYPE=$1; shift
export DOCKER_RUN=$1;shift
# how to auto-discover consul using dns alone!
export SPRING_PROFILE="shell"
export APP_PRIMARY="init"
export APP_IMAGE_NAME="core-init-client"
export APP_MAIN_CLASS="com.demo.chat.init.App"
export APP_VERSION=0.0.1

# makes no use of cloud configuration or config-maps
# TODO: difference between '-D' and '--'
export JAVA_TOOL_OPTIONS="-Dspring.profiles.active=${SPRING_PROFILE} \
-Dapp.service.core.key=${KEYSPACE_TYPE} -Dapp.client.rsocket.core.pubsub -Dapp.client.rsocket.core.index \
-Dapp.client.rsocket.core.persistence -Dapp.client.rsocket.core.key \
-Dspring.cloud.consul.config.enabled=false -Dspring.cloud.consul.discovery.enabled=false \
-Dspring.cloud.consul.enabled=false"

[[ $DOCKER_RUN == "" ]] && exit
[[ $DOCKER_RUN == "build" ]] && MAVEN_ARG="spring-boot:build-image"
[[ $DOCKER_RUN == "run" ]] && MAVEN_ARG="spring-boot:run"

mvn -DimageName=${APP_IMAGE_NAME} -DmainClass=${APP_MAIN_CLASS} $MAVEN_ARG

[[ $DOCKER_RUN == "docker" ]] && docker run --rm -d $APP_IMAGE_NAME:$APP_VERSION
