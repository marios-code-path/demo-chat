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
  export CONSUL_HOST=$(docker inspect -f '{{range.NetworkSettings.Networks}}{{.IPAddress}}{{end}}' ${CONSUL_CONTAINER})
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

function help_message() {
  echo No help message defined.
  exit 1
}

function std_exec() {
  RUN_CMD=$1; shift

  if [[ -z $RUN_CMD ]]; then
    echo "No command specified"
    help_message
    exit 1
  fi

  if declare -F "$RUN_CMD" > /dev/null; then
    $RUN_CMD $@
    exit 0
  else
    echo "Unknown command: $RUN_CMD"
    help_message
  fi
}

function getRunCommands() {
cat > $ENV_FILE << EOF
  echo JAVA_TOOL_OPTIONS = $JAVA_TOOL_OPTIONS
  echo MAVEN ARGS=$MAVEN_PROFILES
  echo MAVEN RUN=$RUN_MAVEN_ARG
EOF
}