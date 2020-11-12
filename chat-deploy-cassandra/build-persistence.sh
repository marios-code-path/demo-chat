export JAVA_TOOL_OPTIONS=" -Dspring.profiles.active=key-service,persistence,user,message,topic,membership -Dserver.port=6601 -Dspring.rsocket.server.port=6600 -Dapp.primary=persistence -Dspring.cloud.consul.host=172.17.0.2 -Dspring.cloud.consul.port=8500 "
export CHAT_IMAGE_NAME="memory-persistence-service"
mvn spring-boot:build-image

