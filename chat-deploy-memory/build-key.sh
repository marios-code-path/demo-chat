export JAVA_TOOL_OPTIONS="-Dspring.profiles.active=key-service -Dserver.port=6501 -Dspring.rsocket.server.port=6500"
export CHAT_IMAGE_NAME="memory-key-service"
mvn spring-boot:build-image
