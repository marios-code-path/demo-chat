#!/bin/bash
#
# Makes the TLS material for a mutually authenticated deployment.
#
#   ./shell-scripts/gen-dckeys.sh <cert-password>
#   PASSWORD=<cert-password> ./shell-scripts/gen-dckeys.sh
#
# It builds one certificate authority, then a server identity and a client
# identity signed by it, and it writes everything into `encrypt-keys/` beside
# this repository. `chat-build --tls DIR` reads that directory, and
# KEYSTORE_PASS must hold the same password.
#
# What it writes, measured on 2026-09-22:
#
#   ca_key.pem ca_pub.pem ca.cer ca.srl   the authority
#   server_key.pem server_pub.pem         the server identity
#   server.cer server.csr server_ca.pem
#   server_keystore.p12 server_truststore.p12
#   client_key.pem client_pub.pem         the client identity
#   client.cer client.csr client_ca.pem
#   client_keystore.p12 client_truststore.p12
#   server.jwk server_keycert.jwk         the authorization server signing key
#
# **server_keycert.jwk is the authorization server signing key.** It holds the
# private d and an x5c chain. Point app.oauth2.jwk.path at it, or pass it as
# chat-build authserv --jwk. The same file goes to a Kubernetes secret through
# devops/k8s/volumes/make-cert-secrets.sh, and to /etc/keys through
# docker_volume_gen, so one artifact serves a local run and a deployed one.
#
# It carried the server public key until 2026-09-22 and could not sign. See
# CHAT-cadftbow.
#
# **Keep the output in encrypt-keys.** That directory is in .gitignore. A
# private key under a tracked directory is one git add away from a commit.
#
# docs/BUILD.md carries the launch commands, and
# docs/DEPLOYMENT-WORKFLOW.md places this script in the wider order of work.
#
# It needs openssl, keytool, jq and eckles on the PATH. eckles converts a PEM
# public key to JWK, and it is an npm package rather than a system tool.
#
# The script runs under `set -x` and does not stop on an error. Read the
# output, and check that `encrypt-keys/` holds every file named above before
# you treat a run as finished.

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
