#!/bin/sh

set -x
set -e

function cert_gen() {
# Using Elipctical Curve Cryptography (ECC) to generate a key pair
# https://wiki.openssl.org/index.php/Command_Line_Elliptic_Curve_Operations
#
PREFIX=$1; shift
VOLUME_NAME="demo-chat-server-keys"
# Server Key
openssl ecparam -name prime256v1 -genkey -noout -out ${PREFIX}_key.pem

# Get Server Public key
openssl pkey -pubout -in ${PREFIX}_key.pem -out ${PREFIX}_pub.pem

# CSR req
openssl req -new -key ${PREFIX}_key.pem -sha256 -out ${PREFIX}.csr -subj "/CN=demochat,OU=demo,O=chat,L=C4Space"

# Sign with CA for CERT
openssl x509 -req -CA ca.cer -CAkey ca_key.pem -in ${PREFIX}.csr -out ${PREFIX}.cer -days 3650 -CAcreateserial -sha256 -passin pass:${PASSWORD}

# Import root Cert, this Cert into a PKCS12 file
cat ca.cer ${PREFIX}.cer > ${PREFIX}ca.pem
openssl pkcs12 -export -in ${PREFIX}ca.pem -inkey ${PREFIX}_key.pem -name localhost -password pass:${PASSWORD} > ${PREFIX}_keystore.p12

# Import Root cert into this trust-store
openssl pkcs12 -export -in ca.cer -inkey ca_key.pem -name caroot -password pass:${PASSWORD} > ${PREFIX}_truststore.p12
}

function ca_gen() {
  # CA Key
  openssl ecparam -name prime256v1 -genkey -noout -out ca_key.pem

  # Get the public key
  openssl pkey -pubout -in ca_key.pem -out ca_pub.pem

  # CA cert
  openssl req -x509 -new -nodes -key ca_key.pem -passout pass:${PASSWORD} -out ca.cer -days 365 -sha256 -subj "/CN=CARoot"
}

function docker_volume_gen() {
  cd ../encrypt-keys

  docker volume create ${VOLUME_NAME}

  docker run -d --rm --name temp-container -v ${VOLUME_NAME}:/etc/keys alpine:latest tail -f /dev/null

  docker cp ./server*.p12 temp-container:/etc/keys
  docker cp ./server*.cer temp-container:/etc/keys
  docker cp ./server*jwk temp-container:/etc/keys
}

export PASSWORD=$1; shift

TMPDIR=/tmp/dckeys$$

mkdir $TMPDIR
here=`pwd`
cd $TMPDIR

ca_gen

cert_gen server

#Eckles Server Public key to JWK
eckles server_pub.pem > server.jwk

# THanks to https://darutk.medium.com/jwk-representing-self-signed-certificate-65276d70021b
# Create Both key and cert  JWK
CERT=$(sed /-/d server.cer | tr -d \\n)
jq ".+{\"x5c\":[\"$CERT\"]}" server.jwk > server_keycert.jwk

## Now the same for the client.

cert_gen client

cd $here

mv $TMPDIR ../encrypt-keys