export CONSUL_CONTAINER=`docker ps -aqf "name=consul"`
export CONSUL_HOST=`./docker-what-is-consul-ip.sh ${CONSUL_CONTAINER}`
export CONSUL_PORT=8500
# run memory core services
# with a deployment name of 'core_services'
# with key type of long
# with EDGE_SERVICES enabled
# with exposed rSOCKET ports on 6970
# with consul discovery enabled
./build-memory-core.sh -n core_services -k long -b rundocker -e -d -c $@
