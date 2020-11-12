export CONSUL_HOST=172.17.0.2
export CONSUL_PORT=8500
export JAVA_TOOL_OPTIONS=" -Dspring.profiles.active=key-service -Dserver.port=6501 -Dspring.rsocket.server.port=6500 -Dspring.cloud.consul.host=172.17.0.2 -Dspring.cloud.consul.port=8500 "
export CHAT_IMAGE_NAME="memory-key-service"
mvn spring-boot:build-image

#java -jar target/chat-deploy-memory-0.0.1.jar --spring.profiles.active=key-service --server.port=6501 --spring.rsocket.server.port=6500 --spring.cloud.consul.host=172.17.0.2 --spring.cloud.consul.host=8500