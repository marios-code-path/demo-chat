export APP_PRIMARY="authserv"
export APP_PROTO="rsocket"
export APP_IMAGE_NAME="chat-authserv"
export MAIN_FLAGS="-Dspring.shell.interactive.enabled=false -Dspring.main.web-application-type=servlet"
export CLIENT_FLAGS="-Dapp.client.protocol=rsocket \
-Dapp.client.rsocket.core.key -Dapp.service.security.userdetails \
-Dapp.client.rsocket.core.persistence -Dapp.client.rsocket.core.index \
-Dapp.client.rsocket.core.secrets -Dapp.client.rsocket.composite.user -Dapp.service.composite.auth"
export SERVICE_FLAGS=""
export PORTS_FLAGS="-Dserver.port=9000 -Dmanagement.server.port=9001"
export KEY_VOLUME=demo-chat-server-keys
source ./util.sh

find_consul

export DOCKER_ARGS="--expose 9000 -p 9000:9000/tcp -v ${KEY_VOLUME}:/etc/keys"
#./build-shell-client-local.sh -m chat-authorization-server -k long -n chat_authserv -b runlocal -l -s /tmp/dc-keys $@

./build-shell-client-local.sh -m chat-authorization-server -k long -n chat_authserv -d -b rundocker -s /etc/keys $@
