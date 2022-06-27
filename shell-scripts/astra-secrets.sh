FILE=$1; shift

# This scripts requires a TOKEN file downloaded from that ASTRA website 

SECRET_KEY=`cat $FILE | jq .secret`
CLIENT_ID=`cat $FILE | jq .clientId`
TOKEN=`cat $FILE | jq .token`

echo "export SECRET_KEY=$SECRET_KEY"
echo "export CLIENT_ID=$CLIENT_ID"
echo "export TOKEN=$TOKEN"
