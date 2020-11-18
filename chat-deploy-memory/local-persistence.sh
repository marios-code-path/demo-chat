mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=6401 --spring.rsocket.server.port=6400 --app.service.persistence --app.primary=persistence --app.client.rsocket.key=key-service-rsocket"
