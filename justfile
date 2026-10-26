set shell := ["zsh", "-eu", "-c"]

# Show the command menu.
default:
	@just --list

# Check the default build-health state.
check:
	./shell-scripts/build-health.sh

# Check the install build-health state.
check-install:
	./shell-scripts/build-health.sh --install

# Check the integration build-health state.
check-integration:
	./shell-scripts/build-health.sh --integration

# Build the container image, then check the build-health state. Resolves online.
check-ci:
	./shell-scripts/build-health.sh --ci

# Check chat-build flag output.
check-flags:
	./shell-scripts/test-flags.sh

# Check image manifest output with stub Maven and Docker commands.
check-image-manifest:
	./shell-scripts/test-image-manifest.sh

# Check the node-9 secrets choices without a live node.
check-node-9-install-secrets:
	./shell-scripts/test-node-9-install-secrets.sh

# Check the register wrapper, the import bundle, and the prepared-host import. Starts no node.
check-register:
	bash shell-scripts/test-node-9-register.sh
	bash shell-scripts/test-register-bundle.sh
	bash shell-scripts/test-register-bootstrap.sh

# Check the local TCP port availability tool. Starts one listener.
check-is-tcp-open:
	bash shell-scripts/test-is-tcp-open.sh

# Check the ssh tunnel tool against a stub ssh. Opens no connection.
check-node-9-tunnel:
	bash shell-scripts/test-node-9-tunnel.sh

# Check the remote command against stub tools. Opens no connection.
check-node-9-remote:
	bash shell-scripts/test-node-9-remote.sh

# Check the durable deploy recipe flags and the chat-deploy stage output. Starts no service.
check-deploy-recipe:
	./shell-scripts/test-deploy-recipe.sh
	./shell-scripts/test-chat-deploy-progress.sh

# Check the flags of the shell launch recipe. Starts no shell.
check-shell-recipe:
	./shell-scripts/test-shell-recipe.sh

# Check that every deployment recipe refuses to run without a key type. Starts nothing.
check-key-type-rule:
	./shell-scripts/test-key-type-required.sh

# Fail when a module pom declares a third-party dependency version.
check-deps:
	./shell-scripts/check-dependency-versions.sh

# Fail when a test artifact can reach a production classpath.
check-production-classpath:
	./shell-scripts/check-production-classpath.sh

# Run the same Maven command as the CI build job.
ci-local:
	mvn -B clean test

# Run the same Maven command as the CI integration job.
ci-integration-local:
	mvn -B clean verify -Ptest-build,integration

# Build selected modules with the manual CI script.
build-partial $modules $mode="unit":
	./shell-scripts/build-partial.sh --modules "$modules" --mode "$mode"

# Print the partial build command without starting Maven.
dry-run-partial $modules $mode="unit":
	./shell-scripts/build-partial.sh --modules "$modules" --mode "$mode" --dry-run

# Check partial build inputs and Maven execution without a build.
check-partial:
	./shell-scripts/test-build-partial.sh

# Flags go to chat-build. A launch needs --key-type TYPE, and no key type is a default.
# Start the core service with memory storage.
[positional-arguments]
launch-memory node_id="0" *flags:
	#!/usr/bin/env bash
	set -euo pipefail
	node_id=0
	if (($# > 0)) && [[ "$1" != -* ]]; then
	    node_id="${1#node_id=}"
	    shift
	fi
	exec ./shell-scripts/chat-build core --memory --run --notls --node-id "$node_id" --init users,rootkeys "$@"

# Print the memory launch command and properties.
[positional-arguments]
dry-run-memory node_id="0" *flags:
	#!/usr/bin/env bash
	set -euo pipefail
	exec just --justfile {{quote(justfile())}} launch-memory "$@" --dry-run

# launch-shell loads these jars from ~/.m2. Flags: --dry-run prints the install command.
# Install chat-shell, chat-shell-host, and the modules they need. Run it after a pull.
[positional-arguments]
build-shell *flags:
	#!/usr/bin/env bash
	set -euo pipefail
	# Under its shell profile, chat-deploy depends on chat-shell, and chat-shell
	# depends on chat-deploy. One reactor refuses that cycle. So this installs
	# chat-shell -am without that profile. test-shell-recipe.sh checks that this
	# reactor holds every module that the shell launch loads. See CHAT-tkijkkbc.
	# chat-shell-host holds the ! command, which a shell launch loads by default.
	cmd=(mvn -B -ntp -pl chat-shell,chat-shell-host -am -Dmaven.test.skip=true install)
	case "${1:-}" in
	    "") ;;
	    --dry-run) printf '%s\n' "${cmd[*]}"; exit 0 ;;
	    *) echo "Unknown build-shell flag: $1" >&2; exit 2 ;;
	esac
	echo "[build-shell] Installing chat-shell, chat-shell-host, and the modules they need."
	"${cmd[@]}"
	echo "[build-shell] Done in $SECONDS s. Start the shell with just launch-shell."

# Build the shell jars and the runtime classpath file for shell-scripts/chat-shell-cli. Run it after a pull.
build-shell-cli:
	#!/usr/bin/env bash
	set -euo pipefail
	just --justfile {{quote(justfile())}} build-shell
	mvn -B -ntp -f chat-deploy/pom.xml -Pdeploy,shell,shell-host,client-backend \
	    -Dmaven.test.skip=true compile dependency:build-classpath \
	    -DincludeScope=runtime -Dmdep.outputFile="$PWD/chat-deploy/target/shell-cli.classpath"
	echo "[build-shell-cli] Done in $SECONDS s. Run shell-scripts/chat-shell-cli."

# Stage the shell CLI runtime for an installed node-9 home. The stage holds
# every runtime jar and the frozen JVM options. node-9/install calls the same
# script. See CHAT-bjqzvsoe.
build-shell-cli-lib stage:
	#!/usr/bin/env bash
	set -euo pipefail
	shell-scripts/build-shell-cli-lib {{quote(stage)}}

# Flags go to chat-build. The shell key type must match the core: use --key-type uuid for a uuid core.
# --build runs build-shell first. The recipe keeps it, because chat-build --build builds an image.
# Start the interactive shell client. Start a core service first. Add --build after a pull.
[positional-arguments]
launch-shell node_id="1" *flags:
	#!/usr/bin/env bash
	set -euo pipefail
	node_id=1
	if (($# > 0)) && [[ "$1" != -* ]]; then
	    node_id="${1#node_id=}"
	    shift
	fi
	build=false
	dry_run=false
	args=()
	for arg in "$@"; do
	    case "$arg" in
	        --build) build=true ;;
	        --dry-run) dry_run=true; args+=("$arg") ;;
	        *) args+=("$arg") ;;
	    esac
	done
	if [[ "$build" == true ]]; then
	    # Check every argument, the key type too, before a long install.
	    ./shell-scripts/chat-build shell --run --notls --node-id "$node_id" \
	        ${args[@]+"${args[@]}"} --dry-run > /dev/null
	    if [[ "$dry_run" == true ]]; then
	        just --justfile {{quote(justfile())}} build-shell --dry-run
	    else
	        just --justfile {{quote(justfile())}} build-shell
	    fi
	fi
	exec ./shell-scripts/chat-build shell --run --notls --node-id "$node_id" ${args[@]+"${args[@]}"}

# Print the shell client launch command and properties.
[positional-arguments]
dry-run-shell node_id="1" *flags:
	#!/usr/bin/env bash
	set -euo pipefail
	exec just --justfile {{quote(justfile())}} launch-shell "$@" --dry-run

# An image is a deployment, so it needs --key-type TYPE. Flags go to chat-build.
# Build a Cassandra core service image.
[positional-arguments]
build-cassandra node_id *flags:
	#!/usr/bin/env bash
	set -euo pipefail
	if [[ "$1" == -* ]]; then
	    echo "build-cassandra needs a node id first, such as: just build-cassandra 16 --key-type uuid" >&2
	    exit 2
	fi
	node_id="${1#node_id=}"
	shift
	exec ./shell-scripts/chat-build core --cassandra --build --notls --node-id "$node_id" "$@"

# Deploy or stop core with Cassandra, Lucene, Redis pub/sub, and a memory command bus.
# Flags: --key-type TYPE selects chat_TYPE. --vector embedded and --embedding openai enable vectors.
# --dry-run prints the plan. --stop stops.
[positional-arguments]
deploy-core-cassandra-lucene-redis node_id="7" *flags:
	#!/usr/bin/env bash
	set -euo pipefail
	node_id=7
	stop=false
	extra=()
	# Read every flag before any action, so a stop never ignores a later flag.
	# chat-build checks the key type value. This recipe holds no list of key types.
	while (($# > 0)); do
	    case "$1" in
	        --stop) stop=true ;;
	        --dry-run) extra+=("$1") ;;
	        --key-type=?*) extra+=(--key-type "${1#--key-type=}") ;;
	        --key-type)
	            if (($# < 2)) || [[ "$2" == -* ]]; then
	                echo "--key-type needs a value" >&2
	                exit 2
	            fi
	            extra+=(--key-type "$2")
	            shift ;;
	        --vector|--embedding)
	            if (($# < 2)) || [[ -z "$2" || "$2" == -* ]]; then
	                echo "$1 needs a provider" >&2
	                exit 2
	            fi
	            extra+=("$1" "$2")
	            shift ;;
	        -*) echo "Unknown deployment flag: $1" >&2; exit 2 ;;
	        *) node_id="${1#node_id=}" ;;
	    esac
	    shift
	done
	if [[ "$stop" == true ]]; then
	    if ((${#extra[@]} > 0)); then
	        echo "--stop takes no other flag" >&2
	        exit 2
	    fi
	    ./shell-scripts/chat-deploy core --stop
	else
	    ./shell-scripts/chat-deploy core --cassandra --index lucene --pubsub redis-pubsub --notls --node-id "$node_id" ${extra[@]+"${extra[@]}"}
	fi

# The image is a deployment, so it needs --key-type TYPE. Match the key type of the core.
# Build and start the authorization server container after checking its dependencies.
[positional-arguments]
deploy-authserv jwk *flags:
	#!/usr/bin/env bash
	set -euo pipefail
	status() { printf '[deploy-authserv] %s\n' "$1"; }
	shift
	key_args=()
	while (($# > 0)); do
	    case "$1" in
	        --key-type=?*) key_args=(--key-type "${1#--key-type=}") ;;
	        --key-type)
	            if (($# < 2)) || [[ "$2" == -* ]]; then
	                echo "--key-type needs a value" >&2
	                exit 2
	            fi
	            key_args=(--key-type "$2")
	            shift ;;
	        *) echo "Unknown deploy-authserv flag: $1" >&2; exit 2 ;;
	    esac
	    shift
	done
	# chat-build states the key type rule. This check runs before every other step.
	CONSUL_HOST=127.0.0.1 ./shell-scripts/chat-build authserv --build --notls --node-id 8 \
	    --consul ${key_args[@]+"${key_args[@]}"} --dry-run > /dev/null
	status "Checking credentials and JWK file."
	CHAT_SERVICE_PASSWORD="${CHAT_SERVICE_PASSWORD:-}"
	SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME:-}"
	SPRING_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD:-}"
	jwk={{quote(jwk)}}
	jwk="${jwk#jwk=}"
	for variable in CHAT_SERVICE_PASSWORD SPRING_DATASOURCE_USERNAME SPRING_DATASOURCE_PASSWORD; do
	    if [[ -z "${!variable}" ]]; then
	        echo "deploy-authserv: set $variable before starting" >&2
	        exit 2
	    fi
	done
	if [[ ! -f "$jwk" ]]; then
	    echo "deploy-authserv: JWK file does not exist: $jwk" >&2
	    exit 2
	fi
	jwk="$(cd "$(dirname "$jwk")" && pwd)/$(basename "$jwk")"
	if jwk_owner_uid="$(stat -c '%u' "$jwk" 2>/dev/null)"; then
	    :
	else
	    jwk_owner_uid="$(stat -f '%u' "$jwk")"
	fi
	if [[ "$jwk_owner_uid" != "$(id -u)" ]]; then
	    echo "deploy-authserv: JWK file must be owned by the user that starts this recipe" >&2
	    exit 2
	fi
	core_health_url="http://127.0.0.1:${CORE_MGMT_PORT:-6791}/actuator/health"
	status "Checking core health at $core_health_url."
	if ! curl --connect-timeout 2 --max-time 3 --fail --silent "$core_health_url" >/dev/null; then
	    echo "deploy-authserv: core is not healthy at $core_health_url" >&2
	    exit 1
	fi
	status "Core is healthy."
	consul_host="${CONSUL_HOST:-127.0.0.1}"
	core_discovery_url="http://${consul_host}:${CONSUL_PORT:-8500}/v1/health/service/core-service-rsocket?passing=true&tag=rsocket"
	status "Checking the core RSocket registration in Consul."
	if ! core_registrations="$(curl --connect-timeout 2 --max-time 3 --fail --silent "$core_discovery_url")"; then
	    echo "deploy-authserv: cannot query Consul at ${consul_host}:${CONSUL_PORT:-8500}" >&2
	    exit 1
	fi
	if ! jq -e 'length > 0' <<<"$core_registrations" >/dev/null; then
	    echo "deploy-authserv: core has no passing RSocket registration in Consul" >&2
	    echo "Rebuild and restart core with the current chat-build command, then retry." >&2
	    exit 1
	fi
	status "Core has a passing RSocket registration."
	auth_health_url="http://127.0.0.1:${AUTHSERV_MGMT_PORT:-9001}/actuator/health"
	auth_status="$(curl --connect-timeout 2 --max-time 3 --silent --output /dev/null --write-out '%{http_code}' "$auth_health_url" || true)"
	if [[ "$auth_status" == 200 ]]; then
	    status "Authserv is already healthy at $auth_health_url."
	    exit 0
	fi
	if [[ "$auth_status" != 000 ]]; then
	    echo "deploy-authserv: authorization server responded with HTTP $auth_status" >&2
	    exit 1
	fi
	compose_file=chat-authorization-server/docker-compose.yaml
	export AUTHSERV_JWK_PATH="$jwk"
	export AUTHSERV_UID="$jwk_owner_uid"
	export AUTHSERV_GID="$(id -g)"
	status "Starting the Postgres container."
	docker compose -f "$compose_file" up -d postgres
	status "Waiting for Postgres database readiness."
	for ((attempt = 1; attempt <= 30; attempt++)); do
	    if docker compose -f "$compose_file" exec -T \
	        -e PGPASSWORD="$SPRING_DATASOURCE_PASSWORD" postgres \
	        psql -U "$SPRING_DATASOURCE_USERNAME" -d authserver -Atqc 'SELECT 1' \
	        >/dev/null 2>&1; then
	        status "Postgres is ready after ${attempt} seconds."
	        break
	    fi
	    if ((attempt == 30)); then
	        echo "deploy-authserv: Postgres did not become ready" >&2
	        exit 1
	    fi
	    if ((attempt % 10 == 0)); then
	        status "Postgres is still starting (${attempt}/30 seconds)."
	    fi
	    sleep 1
	done
	container_consul_host="$consul_host"
	if [[ "$consul_host" == 127.0.0.1 || "$consul_host" == localhost ]]; then
	    container_consul_host=host.docker.internal
	fi
	if [[ -n "${CHAT_BUILD_DOCKER_CONFIG:-}" ]]; then
	    build_docker_config="$CHAT_BUILD_DOCKER_CONFIG"
	else
	    build_docker_config="$(mktemp -d "${TMPDIR:-/tmp}/chat-authserv-docker.XXXXXX")"
	    printf '{}\n' > "$build_docker_config/config.json"
	    trap 'rm -rf -- "$build_docker_config"' EXIT
	fi
	status "Installing Maven reactor dependencies."
	env CONDA_NO_PLUGINS=true conda run -n base mvn -f pom.xml \
	    -pl chat-authorization-server -am -Pjdbc,register-consul,discovery-consul \
	    -Dmaven.test.skip=true -Dkotlin.compiler.daemon=false install
	status "Building the authserv Docker image. Maven will report build progress."
	env CONDA_NO_PLUGINS=true CONSUL_HOST="$container_consul_host" \
	    DOCKER_CONFIG="$build_docker_config" conda run -n base \
	    ./shell-scripts/chat-build authserv --build --notls --node-id 8 --consul \
	    ${key_args[@]+"${key_args[@]}"}
	status "The authserv Docker image build is complete."
	status "Starting the authserv container."
	docker compose -f "$compose_file" up -d authserv
	status "Waiting for authserv health at $auth_health_url."
	for ((attempt = 1; attempt <= 60; attempt++)); do
	    auth_status="$(curl --connect-timeout 2 --max-time 3 --silent --output /dev/null \
	        --write-out '%{http_code}' "$auth_health_url" || true)"
	    if [[ "$auth_status" == 200 ]]; then
	        status "Authserv is healthy at $auth_health_url after ${attempt} seconds."
	        exit 0
	    fi
	    if ((attempt == 60)); then
	        echo "deploy-authserv: authorization server did not become healthy at $auth_health_url" >&2
	        exit 1
	    fi
	    if ((attempt % 10 == 0)); then
	        status "Authserv is still starting (${attempt}/60 seconds)."
	    fi
	    sleep 1
	done

# Generate a private ES256 signing key for the authorization server.
generate-authserv-jwk:
	#!/usr/bin/env bash
	set -euo pipefail
	set +x
	repo_root="$(git rev-parse --show-toplevel)"
	output="$repo_root/encrypt-keys/authserv.jwk"
	if [[ -e "$output" ]]; then
	    echo "Authorization signing key already exists: $output"
	    exit 0
	fi
	mkdir -p "$repo_root/encrypt-keys"
	tmp_dir="$(mktemp -d "${TMPDIR:-/tmp}/authserv-jwk.XXXXXX")"
	trap 'rm -rf -- "$tmp_dir"' EXIT
	classpath_file="$tmp_dir/classpath"
	mvn -q -f "$repo_root/chat-authorization-server/pom.xml" -Pjdbc \
	    dependency:build-classpath -Dmdep.includeScope=runtime \
	    -Dmdep.outputFile="$classpath_file"
	classpath="$(cat "$classpath_file")"
	if [[ -z "$classpath" ]]; then
	    echo "generate-authserv-jwk: Maven returned an empty classpath" >&2
	    exit 1
	fi
	umask 077
	chmod 700 "$repo_root/encrypt-keys"
	export AUTHSERV_JWK_PATH="$output"
	jshell_script="$tmp_dir/generate.jsh"
	printf '%s\n' \
	    'var jwk = new com.nimbusds.jose.jwk.gen.ECKeyGenerator(com.nimbusds.jose.jwk.Curve.P_256).keyID(java.util.UUID.randomUUID().toString()).generate();' \
	    'java.nio.file.Files.writeString(java.nio.file.Path.of(System.getenv("AUTHSERV_JWK_PATH")), jwk.toJSONString(), java.nio.file.StandardOpenOption.CREATE_NEW);' \
	    '/exit' > "$jshell_script"
	jshell --feedback silent --class-path "$classpath" "$jshell_script"
	chmod 600 "$output"
	if [[ ! -s "$output" ]]; then
	    echo "generate-authserv-jwk: no key was written to $output" >&2
	    exit 1
	fi
	echo "Created authorization signing key: $output"
