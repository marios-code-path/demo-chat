export APP_PRIMARY="authserv"
export APP_IMAGE_NAME="chat-authserv"
export MAIN_FLAGS="-Dspring.shell.interactive.enabled=false -Dspring.main.web-application-type=servlet"
export CLIENT_FLAGS="-Dapp.client.protocol=rsocket \
-Dapp.rsocket.transport.unprotected -Dapp.client.rsocket.core.key -Dapp.service.security.userdetails \
-Dapp.client.rsocket.core.persistence -Dapp.client.rsocket.core.index \
-Dapp.client.rsocket.core.secrets -Dapp.client.rsocket.composite.user -Dapp.service.composite.auth"
export SERVICE_FLAGS=""
export MAVEN_PROFILES="-P deploy,client-local,"
export PORTS_FLAGS="-Dserver.port=9000"

export CONSUL_CONTAINER=`docker ps -aqf "name=consul"`
export CONSUL_HOST=`./docker-what-is-consul-ip.sh ${CONSUL_CONTAINER}`
export CONSUL_PORT=8500

export DOCKER_ARGS="--expose 9000 -p 9000:9000/tcp"
./build-authserv-client-local.sh -m chat-authorization-server -k long -n chat_authserv -b runlocal $@
# add -c for consul