export TOKEN_FILE=$1; shift
export KEYSPACE=$1; shift
export KEYSPACE_TYPE=$1; shift

# shellcheck disable=SC2006
SECRET_KEY=`cat $TOKEN_FILE | jq .secret`
# shellcheck disable=SC2006
CLIENT_ID=`cat $TOKEN_FILE | jq .clientId`

export CASSANDRA_OPTIONS="\
-Dspring.cassandra.keyspace-name=\"${KEYSPACE}\" \
-Dspring.cassandra.username=${CLIENT_ID} \
-Dspring.cassandra.password=${SECRET_KEY} "