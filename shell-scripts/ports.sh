export CORE_PORT=${CORE_PORT:=6790}
export EDGE_PORT=${EDGE_PORT:=7790}
export CONSUL_HOST=${CONSUL_HOST:="127.0.0.1"}
export CONSUL_PORT=${CONSUL_PORT:=8500}

export DISCOVERY_ARGS="-Dspring.cloud.consul.config.enabled=false -Dspring.cloud.consul.discovery.enabled=false -Dspring.cloud.consul.enabled=false"
export NOBUILD=false