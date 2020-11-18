mvn spring-boot:run -Dspring-boot.run.arguments=\
"--server.port=6401 --spring.rsocket.server.port=6400 \
--app.service.key --app.service.persistence --app.service.index --spp.service.message --app.primary=core"
