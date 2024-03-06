#!/bin/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"


source $DIR/key_functions.sh

set -x
#set -e

if [ ! -z $1 ]; then
	export PASSWORD=$1; shift
fi

if [ -z $PASSWORD ]; then
	echo either pass in the cert password as first argument, or place in \$PASSWORD
	exit 1
fi

TMPDIR=/tmp/dckeys$$
mkdir $TMPDIR
cd $TMPDIR

ca_gen

cert_gen server

#Eckles Server Public key to JWK
eckles server_pub.pem > server.jwk

# THanks to https://darutk.medium.com/jwk-representing-self-signed-certificate-65276d70021b
# Create Both key and cert  JWK
# add key id (kid) value to the JWK
CERT=$(sed /-/d server.cer | tr -d \\n)
jq ".+{\"x5c\":[\"$CERT\"]}" server.jwk > server_keycert.jwk

## Now the same for the client. sans jwk

cert_gen client

cd $DIR

cp -pRP $TMPDIR $DIR/../encrypt-keys

mkdir -p $DIR/../chat-authorization-server/src/test/resources/
cp -p $DIR/../encrypt-keys/server_keycert.jwk $DIR/../chat-authorization-server/src/test/resources/
