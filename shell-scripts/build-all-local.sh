export IS_CONSUL="-d consul"


./run-rest.sh local -s client -e rest -w -c notls -b build $IS_CONSUL
./run-core.sh runlocal memory -w -c notls -g -b build $IS_CONSUL
./run-authserv.sh local -w -c notls -b build $IS_CONSUL
./run-gateway.sh local -w -c notls -s none -b build $IS_CONSUL
