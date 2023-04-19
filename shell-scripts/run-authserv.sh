export APP_PRIMARY="authserv"
export APP_IMAGE_NAME="chat-authserv"
# Authorization server needs to access user accounts and secrets
export CLIENT_FLAGS="-Dapp.client.protocol=rsocket \
-Dapp.client.rsocket.core.key \
-Dapp.client.rsocket.core.persistence -Dapp.client.rsocket.core.index \
-Dapp.client.rsocket.core.secrets -Dapp.client.rsocket.composite.user"
export SERVICE_FLAGS="-Dapp.service.security.userdetails -Dapp.service.composite.auth"
export PORTS_FLAGS="-Dserver.port=9000 -Dmanagement.server.port=9001"
export OPT_FLAGS="-Dspring.autoconfigure.exclude=org.springframework.boot.autoconfigure.rsocket.RSocketServerAutoConfiguration \
-Dspring.main.web-application-type=servlet"
export MANAGEMENT_ENDPOINTS="shutdown,health"
export ADDITIONAL_CONFIGS="classpath:/config/oauth2-client.yml,"


function authserv_local() {
  ./build-app.sh -m chat-authorization-server -k long -n ${APP_IMAGE_NAME} -d local -b runlocal -c /tmp/dc-keys $@
}

function authserv_docker() {
  export KEY_VOLUME="demo-chat-server-keys"
  export DOCKER_ARGS="--expose 9000 -p 9000:9000/tcp --expose 9001 -p 9001:9001/tcp -v ${KEY_VOLUME}:/etc/keys"

 ./build-app.sh -m chat-authorization-server -k long -n ${APP_IMAGE_NAME} -d consul -b rundocker -c /etc/keys $@
 exit 0
}

RUN_CMD=$1; shift

if declare -F "$RUN_CMD" > /dev/null; then
  $RUN_CMD $@
else
  echo "Unknown command: $RUN_CMD"
  exit 1
fi