function nodeport() {
NAME=$1

export NODE_PORT="$(kubectl get services/$NAME \
 -o go-template='{{(index .spec.ports 0).nodePort}}')"

}

function forward() {
  NAME=$1
  PORT=$2
  kubectl port-forward service/$NAME $PORT:$PORT
}