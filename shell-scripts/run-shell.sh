export APP_PRIMARY="shell"
export APP_IMAGE_NAME="chat-shell"
export MAIN_FLAGS="-Dspring.shell.interactive.enabled=true"
export CLIENT_FLAGS="-Dapp.client.protocol=rsocket \
-Dapp.client.rsocket.core.key \
-Dapp.client.rsocket.core.persistence -Dapp.client.rsocket.core.index -Dapp.client.rsocket.core.pubsub \
-Dapp.client.rsocket.core.secrets -Dapp.client.rsocket.composite.user -Dapp.client.rsocket.composite.message \
-Dapp.client.rsocket.composite.topic"
export SERVICE_FLAGS="-Dapp.service.core.key -Dapp.service.composite.auth"
export PORTS_FLAGS="-Dserver.port=0"

./build-shell-client-local.sh -m chat-shell -k long -p shell -n chat_init -b runlocal -l -s /tmp/dc-keys $@