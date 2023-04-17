export CONSUL_CONTAINER=`docker ps -aqf "name=consul"`
if [[ -z ${CONSUL_CONTAINER} ]]; then
  echo "No consul container found"
  exit 1
fi
export CONSUL_HOST=`./docker-what-is-consul-ip.sh ${CONSUL_CONTAINER}`
export CONSUL_PORT=8500
# run memory core services
# with a deployment name of 'core_services'
# with key type of long
# with EDGE_SERVICES enabled
# with exposed rSOCKET ports on 6970
# with consul discovery enabled
./build-memory-core.sh -n core_services -k long -b rundocker -s file:/etc/keys -d -c $@
