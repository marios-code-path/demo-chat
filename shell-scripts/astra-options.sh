export TOKEN_FILE=$1; shift
export SECURE_CONNECT_PATH=$1; shift
export ASTRA_DB_KEYSPACE=$1; shift

# step 1 - grab a TOKEN File from your Astra Database config page
# step 2 - grab a SECURE CONNECT BUNDLE from the same place
# step 3 - place token file in a safe place
# step 4 - place secure-connect-bundle in a safe place
# step 5 - setup this script to find both
# step 6 - profit!

# shellcheck disable=SC2006
SECRET_KEY=`cat $TOKEN_FILE | jq .secret`
# shellcheck disable=SC2006
CLIENT_ID=`cat $TOKEN_FILE | jq .clientId`
# shellcheck disable=SC2006
TOKEN=`cat $TOKEN_FILE | jq .token`

ABSOLUTE_SCP=`readlink -f $SECURE_CONNECT_PATH`

export CASSANDRA_OPTIONS="\
-Dspring.data.cassandra.keyspace-name=\"${ASTRA_DB_KEYSPACE}\" \
-Dspring.data.cassandra.username=${CLIENT_ID} \
-Dspring.data.cassandra.password=${SECRET_KEY} \
-Dastra.secure-connect-bundle=${ABSOLUTE_SCP}"

#
#curl --request POST \
# --url https://${ASTRA_CLUSTER_ID}-${ASTRA_CLUSTER_REGION}.apps.astra.datastax.com/api/rest/v1/auth \
# --header 'Content-Type: application/json' \
# --data '{"username":"'"$ASTRA_DB_USERNAME"'", "password":"'"$ASTRA_DB_PASSWORD"'"}'
