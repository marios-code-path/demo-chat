export ASTRA_DB_USERNAME=$1; shift
export ASTRA_DB_PASSWORD=$1; shift
export SECURE_CONNECT_PATH=$1; shift
export ASTRA_DB_KEYSPACE=chat
export CASSANDRA_OPTIONS="\
--spring.data.cassandra.keyspace-name=${ASTRA_DB_KEYSPACE} \
--spring.data.cassandra.username=${ASTRA_DB_USERNAME} \
--spring.data.cassandra.password=${ASTRA_DB_PASSWORD} \
--astra.secure-connect-bundle=${SECURE_CONNECT_PATH}"

#
#curl --request POST \
# --url https://${ASTRA_CLUSTER_ID}-${ASTRA_CLUSTER_REGION}.apps.astra.datastax.com/api/rest/v1/auth \
# --header 'Content-Type: application/json' \
# --data '{"username":"'"$ASTRA_DB_USERNAME"'", "password":"'"$ASTRA_DB_PASSWORD"'"}'
