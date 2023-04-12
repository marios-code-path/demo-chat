#!/bin/sh

export PASSWORD=$1; shift

TMPDIR=/tmp/dckeys$$

mkdir $TMPDIR
here=`pwd`
cd $TMPDIR

# CA Key
openssl ecparam -name prime256v1 -genkey -noout -out ca_key.pem

# Get the public key
openssl pkey -pubout -in ca_key.pem -out ca_pub.pem

#cert 
openssl req -x509 -new -nodes -key ca_key.pem -passout pass:${PASSWORD} -out ca.cer -days 365 -sha256 -subj "/CN=CARoot"


# Server Key
openssl ecparam -name prime256v1 -genkey -noout -out server_key.pem

# Get Server Public key
openssl pkey -pubout -in server_key.pem -out server_pub.pem

# CSR req
openssl req -new -key server_key.pem -sha256 -out server.csr -subj "/CN=demochat,OU=demo,O=chat,L=C4Space"

# Sign with CA
openssl x509 -req -CA ca.cer -CAkey ca_key.pem -in server.csr -out server.cer -days 3650 -CAcreateserial -sha256 -passin pass:${PASSWORD}

#Eckles it to JWK 
eckles server_pub.pem > server.jwk

# THanks to https://darutk.medium.com/jwk-representing-self-signed-certificate-65276d70021b
# Create Both key and cert  JWK
CERT=$(sed /-/d server.cer | tr -d \\n)
jq ".+{\"x5c\":[\"$CERT\"]}" server.jwk > server_keycert.jwk


