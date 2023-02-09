export TOKEN_FILE=$1; shift
export ASTRA_DB_KEYSPACE=$1; shift
export KEYSPACE_TYPE=$1; shift

# shellcheck disable=SC2006
SECRET_KEY=`cat $TOKEN_FILE | jq .secret`
# shellcheck disable=SC2006
CLIENT_ID=`cat $TOKEN_FILE | jq .clientId`

export CASSANDRA_OPTIONS="\
-Dspring.cassandra.keyspace-name=\"${ASTRA_DB_KEYSPACE}\" \
-Dspring.cassandra.username=${CLIENT_ID} \
-Dspring.cassandra.password=${SECRET_KEY} "