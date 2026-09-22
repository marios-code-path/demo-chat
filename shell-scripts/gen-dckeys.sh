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

# Eckles the server PRIVATE key to JWK, so the result can sign.
#
# This read server_pub.pem until 2026-09-22, which produced a JWK holding x, y
# and no d. AuthorizationServerConfig hands the parsed JWK to ImmutableJWKSet
# as its signing source and asks for ES256, so that file could not mint a
# token. Both consumers of server_keycert.jwk ship it to a server that signs:
# devops/k8s/volumes/make-cert-secrets.sh builds a secret from it, and
# docker_volume_gen copies it to /etc/keys. See CHAT-cadftbow.
#
# The published JWK set does not leak the private half. Spring's
# NimbusJwkSetEndpointFilter calls JWKSet.toString(), which is
# toJSONObject(publicKeysOnly=true). Measured on 2026-09-22.
eckles server_key.pem > server.jwk

# THanks to https://darutk.medium.com/jwk-representing-self-signed-certificate-65276d70021b
# Create Both key and cert  JWK
# add key id (kid) value to the JWK
CERT=$(sed /-/d server.cer | tr -d \\n)
jq ".+{\"x5c\":[\"$CERT\"]}" server.jwk > server_keycert.jwk

## Now the same for the client. sans jwk

cert_gen client

cd $DIR

cp -pRP $TMPDIR $DIR/../encrypt-keys

# There was a copy of server_keycert.jwk into
# chat-authorization-server/src/test/resources/ here until 2026-09-22.
#
# It is gone for two reasons. No test reads that file, because
# AuthorizationServerTestSigningKey generates a key per run into a temporary
# file. That is the B4 row of docs/BUILD-HEALTH.md. And the file now holds a
# private key, while src/test/resources is tracked by git. encrypt-keys is in
# .gitignore, so the key stays out of a commit as long as it stays there.
