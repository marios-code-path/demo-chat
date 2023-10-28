docker run -d -p 8500:8500 \
 -p 8600:8600/udp \
 --name consul \
 --hostname consul \
 --network mynet \
 -e CONSUL_BIND_INTERFACE=eth0 hashicorp/consul
sleep 5
docker exec -t consul consul members
