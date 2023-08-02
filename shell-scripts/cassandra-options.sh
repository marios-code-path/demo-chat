function cassandra_options() {
CASSANDRA_CONTACT_POINTS=$1; shift
KEYSPACE=$1; shift
CLIENT_ID=$1; shift
SECRET_KEY=${CASSANDRA_SECRET_KEY:=$1};

if [[ -z ${SECRET_KEY} ]]; then
    echo "Cassandra secret key (or password) is required to be sent as argument or set in CASSANDRA_SECRET_KEY"
    exit 1
fi

export CASSANDRA_OPTIONS=" -Dspring.cassandra.keyspace-name=\"${KEYSPACE}\" \
-Dspring.cassandra.username=${CLIENT_ID} \
-Dspring.cassandra.password=${SECRET_KEY} \
-Dspring.cassandra.contact-points=${CASSANDRA_CONTACT_POINTS} \
-Dspring.cassandra.base-packages=com.demo.chat.repository.cassandra \
-Dspring.cassandra.request.consistency=one \
-Dspring.cassandra.request.serial-consistency=any \
-Dspring.cassandra.local-datacenter=datacenter1"

}