export APP_PRIMARY="authserv"
export APP_IMAGE_NAME="chat-authserv"
export MAIN_FLAGS="-Dspring.shell.interactive.enabled=false -Dspring.main.web-application-type=servlet"
export CLIENT_FLAGS="-Dapp.client.protocol=rsocket \
-Dapp.rsocket.transport.unprotected -Dapp.client.rsocket.core.key -Dapp.service.security.userdetails \
-Dapp.client.rsocket.core.persistence -Dapp.client.rsocket.core.index -Dapp.client.rsocket.core.pubsub \
-Dapp.client.rsocket.core.secrets -Dapp.client.rsocket.composite.user -Dapp.client.rsocket.composite.message \
-Dapp.client.rsocket.composite.topic"
export SERVICE_FLAGS="-Dapp.service.core.key -Dapp.service.composite.auth"
export MAVEN_PROFILES="-Pdeploy,client-local"
export PORTS_FLAGS="-Dserver.port=9000"

./build-shell-client-local.sh -m chat-authorization-server -k long -n chat_authserv -b runlocal $@
# add -c for consul