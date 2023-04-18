function cert_gen() {
# Using Elipctical Curve Cryptography (ECC) to generate a key pair
# https://wiki.openssl.org/index.php/Command_Line_Elliptic_Curve_Operations
#
PREFIX=$1

if [[ -z $PREFIX ]]; then
  echo "select a prefix to give key files (e.g. server client)"
  return 1
fi

shift

VOLUME_NAME="demo-chat-server-keys"
# Server Key
openssl ecparam -name prime256v1 -genkey -noout -out ${PREFIX}_key.pem

# Get Server Public key
openssl pkey -pubout -in ${PREFIX}_key.pem -out ${PREFIX}_pub.pem

# CSR req
openssl req -new -key ${PREFIX}_key.pem -sha256 -out ${PREFIX}.csr -subj "/CN=localhost/OU=demo/O=chat/L=C4Space"

# Sign with CA for CERT
openssl x509 -req -CA ca.cer -CAkey ca_key.pem -in ${PREFIX}.csr -out ${PREFIX}.cer -days 3650 -CAcreateserial -sha256 -passin pass:${PASSWORD}

# Import root Cert, this Cert into a PKCS12 file
cat ca.cer ${PREFIX}.cer > ${PREFIX}_ca.pem
openssl pkcs12 -export -in ${PREFIX}_ca.pem -inkey ${PREFIX}_key.pem -name localhost -password pass:${PASSWORD} > ${PREFIX}_keystore.p12

# Import Root cert into this trust-store
openssl pkcs12 -export -in ca.cer -inkey ca_key.pem -name caroot -password pass:${PASSWORD} > ${PREFIX}_truststore.p12
}

function ca_gen() {
  if [[ -z ${PASSWORD} ]]; then
    echo "No password set in \$PASSWORD"
    return 1
  fi

  # CA Key
  openssl ecparam -name prime256v1 -genkey -noout -out ca_key.pem

  # Get the public key
  openssl pkey -pubout -in ca_key.pem -out ca_pub.pem

  # CA cert
  openssl req -x509 -new -nodes -key ca_key.pem -passout pass:${PASSWORD} -out ca.cer -days 365 -sha256 -subj "/CN=chatRoot"
}

function docker_volume_gen() {
  cd ../encrypt-keys

  docker volume create ${VOLUME_NAME}

  docker run -d --rm --name temp-container -v ${VOLUME_NAME}:/etc/keys alpine:latest tail -f /dev/null

  docker cp ./server_keystore.p12 temp-container:/etc/keys
  docker cp ./server_truststore.p12 temp-container:/etc/keys
  docker cp ./server_keycert.jwk temp-container:/etc/keys
}