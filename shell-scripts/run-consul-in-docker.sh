docker run -d -p 8500:8500 \
 -p 8600:8600/udp \
 --name dev-consul \
 -e CONSUL_BIND_INTERFACE=eth0 consul
sleep 5
docker exec -t dev-consul consul members