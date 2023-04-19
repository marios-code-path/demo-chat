function array_contains() {
    local array="$1[@]"
    local seeking=$2
    local in=1
    for element in "${!array}"; do
        if [[ $element == $seeking ]]; then
            in=0
            break
        fi
    done
    echo $in
    return $in
}

function join_by {
  local d=${1-} f=${2-}
  if shift 2; then
    printf %s "$f" "${@/#/$d}"
  fi
}

function find_consul {
  export CONSUL_CONTAINER=`docker ps -aqf "name=consul"`
  export CONSUL_HOST=`./docker-what-is-consul-ip.sh ${CONSUL_CONTAINER}`
  export CONSUL_PORT=8500
}

function expand_delimited {
  local DELIMITED=$1; shift
  local PLACEHOLDERSTRING=$1; shift

  OLDIFS=$IFS
  IFS=','
    read -ra sections <<< "$DELIMITED"
    RESULT=""
    for section in "${sections[@]}"; do
      if [[ ! -z ${section} ]]; then
        RESULT+=${PLACEHOLDERSTRING//__/$section}

      fi
    done
  IFS="$OLDIFS"

  printf "%b" "$RESULT"
}